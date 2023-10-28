package xyz.jpenilla.gremlin.runtime.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.jspecify.annotations.NullMarked;

@NullMarked
public enum HashingAlgorithm {
    SHA256("SHA-256");

    private final String algorithmName;

    HashingAlgorithm(final String algorithmName) {
        this.algorithmName = algorithmName;
    }

    public MessageDigest digest() {
        try {
            return MessageDigest.getInstance(this.algorithmName);
        } catch (final NoSuchAlgorithmException ex) {
            throw new RuntimeException("Could not get MessageDigest instance for '" + this.algorithmName + "' algorithm.", ex);
        }
    }

    public HashResult hashFile(final Path file) throws IOException {
        return calculateHash(Files.newInputStream(file), this.digest());
    }

    public HashResult hashString(final String s) throws IOException {
        return calculateHash(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)), this.digest());
    }

    public record HashResult(byte[] bytes) {
        public String asHexString() {
            return asHexString(this.bytes);
        }

        private static String asHexString(final byte[] bytes) {
            final StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (final byte b : bytes) {
                sb.append("%02x".formatted(b & 0xFF));
            }
            return sb.toString();
        }
    }

    @SuppressWarnings("StatementWithEmptyBody")
    private static HashResult calculateHash(final InputStream inputStream, final MessageDigest digest) throws IOException {
        final DigestInputStream stream = new DigestInputStream(inputStream, digest);
        try (stream) {
            final byte[] buffer = new byte[1024];
            while (stream.read(buffer) != -1) {
                // reading
            }
        }
        return new HashResult(stream.getMessageDigest().digest());
    }
}
