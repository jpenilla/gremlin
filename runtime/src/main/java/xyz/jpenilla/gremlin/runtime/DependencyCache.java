package xyz.jpenilla.gremlin.runtime;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.stream.Stream;
import org.jspecify.annotations.NullMarked;
import xyz.jpenilla.gremlin.runtime.util.Util;

@NullMarked
public final class DependencyCache {
    private final Path dir;

    public DependencyCache(final Path cacheDirectory) {
        this.dir = cacheDirectory;
    }

    public Path cacheDirectory() {
        return this.dir;
    }

    /**
     * Delete cached entries that haven't been used/resolved for over an hour.
     */
    public void cleanup() {
        this.cleanup(1, ChronoUnit.HOURS);
    }

    /**
     * Delete cached entries that haven't been used/resolved for the provided
     * time period.
     *
     * @param deleteUnusedFor unused time
     * @param unit            unused time unit
     */
    public void cleanup(final long deleteUnusedFor, final TemporalUnit unit) {
        try (final Stream<Path> s = Files.walk(this.dir)) {
            s.forEach(f -> {
                if (Files.isRegularFile(f) && f.getFileName().toString().endsWith(".jar")) {
                    final long lastUsed = DependencyResolver.lastUsed(f);
                    if (lastUsed == -1) {
                        return;
                    }
                    final long sinceUsed = System.currentTimeMillis() - lastUsed;
                    if (sinceUsed > Duration.of(deleteUnusedFor, unit).toMillis()) {
                        try {
                            Files.delete(f);
                            Files.deleteIfExists(DependencyResolver.lastUsedFile(f));
                        } catch (final IOException e) {
                            throw Util.rethrow(e);
                        }
                    }
                }
            });
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to clean cache", e);
        }
    }
}
