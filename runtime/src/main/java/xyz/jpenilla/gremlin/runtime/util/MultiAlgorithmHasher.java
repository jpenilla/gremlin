package xyz.jpenilla.gremlin.runtime.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class MultiAlgorithmHasher {
    private final HashingAlgorithm[] algorithms;

    public MultiAlgorithmHasher(final HashingAlgorithm... algorithm) {
        this.algorithms = algorithm;
        if (this.algorithms.length == 0) {
            throw new IllegalArgumentException("No algorithms provided");
        }
        for (final HashingAlgorithm algo : this.algorithms) {
            Objects.requireNonNull(algo);
        }
    }

    public Map<HashingAlgorithm, HashResult> hashString(final String s) throws IOException {
        return this.hash(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)));
    }

    public Map<HashingAlgorithm, HashResult> hashFile(final Path file) throws IOException {
        return this.hash(Files.newInputStream(file));
    }

    public Map<HashingAlgorithm, HashResult> hash(final InputStream stream) throws IOException {
        final MessageDigest[] digests = new MessageDigest[this.algorithms.length];
        for (int i = 0; i < this.algorithms.length; i++) {
            digests[i] = this.algorithms[i].digest();
        }

        try (stream) {
            final byte[] buffer = new byte[8192];
            while (true) {
                final int count = stream.read(buffer);
                if (count == -1) {
                    break;
                }
                for (final MessageDigest digest : digests) {
                    digest.update(buffer, 0, count);
                }
            }
        }

        final Map<HashingAlgorithm, HashResult> resultMap = new HashMap<>();
        for (int i = 0; i < digests.length; i++) {
            final HashingAlgorithm algo = this.algorithms[i];
            final MessageDigest digest = digests[i];
            resultMap.put(algo, new HashResult(digest.digest()));
        }

        return resultMap;
    }
}
