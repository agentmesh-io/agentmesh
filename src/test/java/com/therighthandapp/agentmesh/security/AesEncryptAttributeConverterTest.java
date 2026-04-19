package com.therighthandapp.agentmesh.security;

import org.junit.jupiter.api.*;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AES-256-GCM PII encryption converter.
 * Tests encrypt/decrypt round-trip, null handling, and passthrough mode.
 */
class AesEncryptAttributeConverterTest {

    private AesEncryptAttributeConverter converter;

    // A valid 32-byte key, Base64-encoded (for test only)
    private static final String TEST_KEY_BASE64 = Base64.getEncoder()
            .encodeToString("01234567890123456789012345678901".getBytes());

    @BeforeEach
    void setUp() {
        converter = new AesEncryptAttributeConverter();
    }

    @Test
    @DisplayName("Null input returns null on encrypt")
    void nullInput_encrypt_returnsNull() {
        assertNull(converter.convertToDatabaseColumn(null));
    }

    @Test
    @DisplayName("Null input returns null on decrypt")
    void nullInput_decrypt_returnsNull() {
        assertNull(converter.convertToEntityAttribute(null));
    }

    @Test
    @DisplayName("Empty string returns empty on encrypt")
    void emptyInput_encrypt_returnsEmpty() {
        assertEquals("", converter.convertToDatabaseColumn(""));
    }

    @Test
    @DisplayName("Empty string returns empty on decrypt")
    void emptyInput_decrypt_returnsEmpty() {
        assertEquals("", converter.convertToEntityAttribute(""));
    }

    @Test
    @DisplayName("Passthrough mode when no PII_ENCRYPTION_KEY is set")
    void noEncryptionKey_passthrough() {
        // Ensure no key is set (default in test env)
        clearEnvKey();
        String input = "Acme Corp";
        assertEquals(input, converter.convertToDatabaseColumn(input));
    }

    @Test
    @DisplayName("Unencrypted data returns as-is on decrypt (migration-friendly)")
    void unencryptedData_decrypt_returnsAsIs() {
        clearEnvKey();
        String plaintext = "Acme Corp";
        assertEquals(plaintext, converter.convertToEntityAttribute(plaintext));
    }

    @Test
    @DisplayName("Round-trip encrypt/decrypt preserves plaintext")
    void roundTrip_preservesPlaintext() {
        setEnvKey(TEST_KEY_BASE64);
        try {
            String[] testValues = {
                    "Acme Corp",
                    "Hello World 🌍",
                    "user@example.com",
                    "A",
                    "A very long organization name that exceeds typical VARCHAR(255) limits when combined with encryption overhead"
            };

            for (String plaintext : testValues) {
                String encrypted = converter.convertToDatabaseColumn(plaintext);
                assertNotNull(encrypted, "Encrypted value should not be null for: " + plaintext);
                assertTrue(encrypted.startsWith("ENC::"), "Encrypted value should start with ENC:: prefix");
                assertNotEquals(plaintext, encrypted, "Encrypted value should differ from plaintext");

                String decrypted = converter.convertToEntityAttribute(encrypted);
                assertEquals(plaintext, decrypted, "Decrypted value should match original plaintext");
            }
        } finally {
            clearEnvKey();
        }
    }

    @Test
    @DisplayName("Same plaintext produces different ciphertext (random IV)")
    void sameInput_differentCiphertext() {
        setEnvKey(TEST_KEY_BASE64);
        try {
            String plaintext = "Acme Corp";
            String encrypted1 = converter.convertToDatabaseColumn(plaintext);
            String encrypted2 = converter.convertToDatabaseColumn(plaintext);

            assertNotEquals(encrypted1, encrypted2,
                    "Each encryption should produce different ciphertext due to random IV");

            // But both should decrypt to the same value
            assertEquals(plaintext, converter.convertToEntityAttribute(encrypted1));
            assertEquals(plaintext, converter.convertToEntityAttribute(encrypted2));
        } finally {
            clearEnvKey();
        }
    }

    @Test
    @DisplayName("Wrong key fails to decrypt")
    void wrongKey_failsToDecrypt() {
        setEnvKey(TEST_KEY_BASE64);
        String encrypted;
        try {
            encrypted = converter.convertToDatabaseColumn("Sensitive Data");
        } finally {
            clearEnvKey();
        }

        // Set a different key
        String otherKey = Base64.getEncoder()
                .encodeToString("ABCDEFGHIJKLMNOPQRSTUVWXYZ123456".getBytes());
        setEnvKey(otherKey);
        try {
            assertThrows(IllegalStateException.class, () ->
                            converter.convertToEntityAttribute(encrypted),
                    "Decryption with wrong key should fail (AES-GCM auth tag mismatch)");
        } finally {
            clearEnvKey();
        }
    }

    // --- Helpers to set/clear system property for testing ---

    private static void setEnvKey(String value) {
        System.setProperty("PII_ENCRYPTION_KEY", value);
    }

    private static void clearEnvKey() {
        System.clearProperty("PII_ENCRYPTION_KEY");
    }
}

