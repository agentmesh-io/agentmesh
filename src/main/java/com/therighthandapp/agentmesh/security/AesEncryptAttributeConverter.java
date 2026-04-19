package com.therighthandapp.agentmesh.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * JPA AttributeConverter that transparently encrypts/decrypts PII fields
 * using AES-256-GCM (Authenticated Encryption with Associated Data).
 *
 * <p>Per Architect Protocol v7.17 §5: OWASP Top 10 + AES-256 for PII on SSD.</p>
 *
 * <h3>Usage:</h3>
 * <pre>
 * &#64;Convert(converter = AesEncryptAttributeConverter.class)
 * private String name;
 * </pre>
 *
 * <h3>Configuration:</h3>
 * Set the encryption key via environment variable:
 * <pre>
 * PII_ENCRYPTION_KEY=base64-encoded-32-byte-key
 * </pre>
 *
 * Generate a key: {@code openssl rand -base64 32}
 */
@Converter
public class AesEncryptAttributeConverter implements AttributeConverter<String, String> {

    private static final Logger log = LoggerFactory.getLogger(AesEncryptAttributeConverter.class);

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;    // 96 bits — NIST recommended
    private static final int GCM_TAG_LENGTH = 128;   // 128-bit auth tag
    private static final String PREFIX = "ENC::";     // Marker to distinguish encrypted values

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Lazy-loaded encryption key from environment variable.
     * Returns null if PII_ENCRYPTION_KEY is not set (passthrough mode).
     */
    private static SecretKey getKey() {
        String encodedKey = System.getenv("PII_ENCRYPTION_KEY");
        if (encodedKey == null || encodedKey.isBlank()) {
            return null;
        }
        byte[] decodedKey = Base64.getDecoder().decode(encodedKey);
        if (decodedKey.length != 32) {
            throw new IllegalStateException(
                    "PII_ENCRYPTION_KEY must be exactly 32 bytes (256 bits). Got " + decodedKey.length + " bytes. " +
                    "Generate with: openssl rand -base64 32");
        }
        return new SecretKeySpec(decodedKey, "AES");
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return attribute;
        }

        SecretKey key = getKey();
        if (key == null) {
            // Passthrough mode — no encryption key configured
            return attribute;
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] cipherText = cipher.doFinal(attribute.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // Prepend IV to ciphertext: [IV (12 bytes) | ciphertext + auth tag]
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
            buffer.put(iv);
            buffer.put(cipherText);

            return PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            log.error("Failed to encrypt PII field", e);
            throw new IllegalStateException("PII encryption failed", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return dbData;
        }

        // If not encrypted (no prefix), return as-is (migration-friendly)
        if (!dbData.startsWith(PREFIX)) {
            return dbData;
        }

        SecretKey key = getKey();
        if (key == null) {
            log.warn("Encrypted PII field found but PII_ENCRYPTION_KEY is not set. Cannot decrypt.");
            return dbData; // Return raw — caller must handle
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(dbData.substring(PREFIX.length()));

            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to decrypt PII field", e);
            throw new IllegalStateException("PII decryption failed", e);
        }
    }
}

