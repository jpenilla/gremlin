/*
 * gremlin
 *
 * Copyright (c) 2025 Jason Penilla
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
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class Util {
    private Util() {
    }

    public static void shutdownExecutor(final ExecutorService service, final TimeUnit timeoutUnit, final long timeoutLength) {
        service.shutdown();
        boolean didShutdown;
        try {
            didShutdown = service.awaitTermination(timeoutLength, timeoutUnit);
        } catch (final InterruptedException ignore) {
            didShutdown = false;
        }
        if (!didShutdown) {
            service.shutdownNow();
        }
    }

    /**
     * Attempts to create the parent directories of {@code path} if necessary.
     *
     * <p>Returns {@code path} when successful.</p>
     *
     * @param path path
     * @return {@code path}
     * @throws IOException on I/O error
     */
    public static Path mkParentDirs(final Path path) throws IOException {
        final Path parent = path.getParent();
        if (parent != null && !Files.isDirectory(parent)) {
            try {
                Files.createDirectories(parent);
            } catch (final FileAlreadyExistsException ex) {
                if (!Files.isDirectory(parent)) {
                    throw ex;
                }
            }
        }
        return path;
    }

    @SuppressWarnings("unchecked")
    public static <X extends Throwable> RuntimeException rethrow(final Throwable t) throws X {
        throw (X) t;
    }

    public static URL classpathUrl(final Class<?> cls) {
        try {
            URL sourceUrl = cls.getProtectionDomain().getCodeSource().getLocation();
            // Some class loaders give the full url to the class, some give the URL to its jar.
            // We want the containing jar, so we will unwrap jar-schema code sources.
            if (sourceUrl.getProtocol().equals("jar")) {
                final int exclamationIdx = sourceUrl.getPath().lastIndexOf('!');
                if (exclamationIdx != -1) {
                    sourceUrl = new URL(sourceUrl.getPath().substring(0, exclamationIdx));
                }
            }
            return sourceUrl;
        } catch (final MalformedURLException ex) {
            throw new RuntimeException("Could not locate classpath of " + cls.getName(), ex);
        }
    }

    public static <T extends Comparable<T>> List<T> sorted(final Collection<T> list) {
        final List<T> sorted = new ArrayList<>(list);
        sorted.sort(null);
        return Collections.unmodifiableList(sorted);
    }

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    public static String asHexString(final byte[] bytes) {
        final char[] chars = new char[2 * bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            final byte b = bytes[i];
            final int unsigned = (int) b & 0xFF;
            chars[2 * i] = HEX_CHARS[unsigned / 16];
            chars[2 * i + 1] = HEX_CHARS[unsigned % 16];
        }
        return new String(chars);
    }
}
