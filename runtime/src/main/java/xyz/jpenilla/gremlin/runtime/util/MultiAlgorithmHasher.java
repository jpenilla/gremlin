package xyz.jpenilla.gremlin.runtime.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.EnumMap;
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

    public HashesMap hashString(final String s) throws IOException {
        return this.hash(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)));
    }

    public HashesMap hashFile(final Path file) throws IOException {
        return this.hash(Files.newInputStream(file));
    }

    public HashesMap hash(final InputStream stream) throws IOException {
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

        final HashesMap resultMap = new HashesMapImpl();
        for (int i = 0; i < digests.length; i++) {
            final HashingAlgorithm algo = this.algorithms[i];
            final MessageDigest digest = digests[i];
            resultMap.put(algo, new HashResult(digest.digest()));
        }

        return resultMap;
    }

    public interface HashesMap extends Map<HashingAlgorithm, HashResult> {
        /**
         * Get the specified hash, throwing an exception
         * when it's not present.
         *
         * @param algo hashing algorithm
         * @return hash result
         */
        HashResult hash(HashingAlgorithm algo);
    }

    private static final class HashesMapImpl extends EnumMap<HashingAlgorithm, HashResult> implements HashesMap {
        HashesMapImpl() {
            super(HashingAlgorithm.class);
        }

        @Override
        public HashResult hash(final HashingAlgorithm algo) {
            return Objects.requireNonNull(this.get(algo), "Missing " + algo.name() + " hash");
        }
    }
}
