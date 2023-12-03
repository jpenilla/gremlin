/*
 * gremlin
 *
 * Copyright (c) 2023 Jason Penilla
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package xyz.jpenilla.gremlin.runtime.util;

import java.io.IOException;
import java.io.InputStream;
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

    public HashesMap hashString(final String s) {
        final HashesMap resultMap = new HashesMapImpl(this.algorithms.length);
        for (final HashingAlgorithm algo : this.algorithms) {
            resultMap.put(algo, algo.hashString(s));
        }
        return resultMap;
    }

    public HashesMap hashFile(final Path file) throws IOException {
        try (final InputStream in = Files.newInputStream(file)) {
            return this.hash(in);
        }
    }

    public HashesMap hash(final InputStream stream) throws IOException {
        final MessageDigest[] digests = new MessageDigest[this.algorithms.length];
        for (int i = 0; i < this.algorithms.length; i++) {
            digests[i] = this.algorithms[i].digest();
        }

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

        final HashesMap resultMap = new HashesMapImpl(digests.length);
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

    private static final class HashesMapImpl extends HashMap<HashingAlgorithm, HashResult> implements HashesMap {
        HashesMapImpl(final int size) {
            super(size);
        }

        @Override
        public HashResult hash(final HashingAlgorithm algo) {
            return Objects.requireNonNull(this.get(algo), "Missing " + algo.algorithmName() + " hash");
        }
    }
}
