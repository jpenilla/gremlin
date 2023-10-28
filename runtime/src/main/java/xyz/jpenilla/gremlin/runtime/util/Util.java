package xyz.jpenilla.gremlin.runtime.util;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
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
}
