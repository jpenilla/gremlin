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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record HashingAlgorithm(String algorithmName) {
    public static final HashingAlgorithm SHA1 = new HashingAlgorithm("SHA-1");
    public static final HashingAlgorithm SHA256 = new HashingAlgorithm("SHA-256");

    public HashingAlgorithm {
        Objects.requireNonNull(algorithmName, "algorithmName must not be null");
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

    public HashResult hashString(final String s) {
        final byte[] hash = this.digest().digest(s.getBytes(StandardCharsets.UTF_8));
        return new HashResult(hash);
    }

    public HashResult hash(final InputStream stream) throws IOException {
        return calculateHash(stream, this.digest());
    }

    private static HashResult calculateHash(final InputStream stream, final MessageDigest digest) throws IOException {
        final byte[] buffer = new byte[8192];
        while (true) {
            final int count = stream.read(buffer);
            if (count == -1) {
                break;
            }
            digest.update(buffer, 0, count);
        }
        return new HashResult(digest.digest());
    }
}
