package com.therighthandapp.agentmesh.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Set;

/**
 * Loads RSA key pair for JWT signing/verification (M13.2).
 *
 * <p>Keys live on the Blackhole SSD per Architect Protocol §1 / §5.
 * Default location: {@code ${agentmesh.security.jwt.keys-dir}/private_key.pem}
 * + {@code public_key.pem}. If absent, a 2048-bit pair is generated
 * automatically (POSIX mode 0600 on private). This keeps developer setup
 * to one command — Protocol §2 (offline-first) and §4 (zero-blockade).
 */
@Component
@Slf4j
public class RsaKeyProvider {

    private final Path keysDir;
    private final Path privateKeyPath;
    private final Path publicKeyPath;

    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;
    private String keyId;

    public RsaKeyProvider(
            @Value("${agentmesh.security.jwt.keys-dir:${user.home}/.agentmesh/jwt}") String keysDirPath
    ) {
        this.keysDir = Path.of(keysDirPath);
        this.privateKeyPath = keysDir.resolve("private_key.pem");
        this.publicKeyPath = keysDir.resolve("public_key.pem");
    }

    @PostConstruct
    public void init() throws Exception {
        Files.createDirectories(keysDir);
        if (!Files.exists(privateKeyPath) || !Files.exists(publicKeyPath)) {
            log.warn("[security] No JWT key pair at {} — generating 2048-bit RSA pair", keysDir);
            generateAndPersistKeyPair();
        }
        this.privateKey = loadPrivateKey();
        this.publicKey = loadPublicKey();
        this.keyId = computeKeyId(this.publicKey);
        log.info("[security] JWT key pair loaded keyId={} dir={}", keyId, keysDir);
    }

    public RSAPrivateKey privateKey() { return privateKey; }
    public RSAPublicKey publicKey() { return publicKey; }
    public String keyId() { return keyId; }

    // --- internals ----------------------------------------------------------

    private void generateAndPersistKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048, SecureRandom.getInstanceStrong());
        KeyPair pair = kpg.generateKeyPair();
        writePem(privateKeyPath, "PRIVATE KEY", pair.getPrivate().getEncoded());
        writePem(publicKeyPath, "PUBLIC KEY", pair.getPublic().getEncoded());
        applyPosixMode(privateKeyPath, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
    }

    private static void writePem(Path path, String label, byte[] der) throws IOException {
        String b64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der);
        String pem = "-----BEGIN " + label + "-----\n" + b64 + "\n-----END " + label + "-----\n";
        Files.writeString(path, pem, StandardCharsets.US_ASCII,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    private static void applyPosixMode(Path path, Set<PosixFilePermission> perms) {
        try {
            Files.setPosixFilePermissions(path, perms);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Non-POSIX FS (Windows) — skip silently
        }
    }

    private RSAPrivateKey loadPrivateKey() throws Exception {
        byte[] der = readPemBody(privateKeyPath);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private RSAPublicKey loadPublicKey() throws Exception {
        byte[] der = readPemBody(publicKeyPath);
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    private static byte[] readPemBody(Path path) throws IOException {
        String pem = Files.readString(path, StandardCharsets.US_ASCII);
        String body = pem
                .replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s+", "");
        return Base64.getDecoder().decode(body);
    }

    private static String computeKeyId(RSAPublicKey pub) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(pub.getEncoded());
            // First 8 bytes hex = stable short kid
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", hash[i]));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "unknown";
        }
    }
}

