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
}
