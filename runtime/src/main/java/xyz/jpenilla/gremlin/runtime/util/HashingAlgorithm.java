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
    SHA1("SHA-1"),
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
        return this.hash(Files.newInputStream(file));
    }

    public HashResult hashString(final String s) throws IOException {
        return this.hash(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)));
    }

    public HashResult hash(final InputStream stream) throws IOException {
        return calculateHash(stream, this.digest());
    }

    @SuppressWarnings("StatementWithEmptyBody")
    private static HashResult calculateHash(final InputStream inputStream, final MessageDigest digest) throws IOException {
        final DigestInputStream stream = new DigestInputStream(inputStream, digest);
        try (stream) {
            final byte[] buffer = new byte[8192];
            while (stream.read(buffer) != -1) {
                // reading
            }
        }
        return new HashResult(stream.getMessageDigest().digest());
    }
}
