package xyz.jpenilla.gremlin.runtime;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.List;
import java.util.stream.Stream;
import org.jspecify.annotations.NullMarked;

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
            for (final Path f : s.toList()) {
                if (Files.isRegularFile(f) && f.getFileName().toString().endsWith(".jar")) {
                    final long lastUsed = DependencyResolver.lastUsed(f);
                    if (lastUsed == -1) {
                        continue;
                    }
                    final long sinceUsed = System.currentTimeMillis() - lastUsed;
                    if (sinceUsed > Duration.of(deleteUnusedFor, unit).toMillis()) {
                        Files.delete(f);
                        Files.deleteIfExists(DependencyResolver.lastUsedFile(f));
                        this.deleteEmptyParents(f);
                    }
                }
            }
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to clean cache", e);
        }
    }

    private void deleteEmptyParents(final Path path) throws IOException {
        final Path parent = path.getParent();
        if (parent.toAbsolutePath().equals(this.dir.toAbsolutePath())) {
            return;
        }
        final List<Path> siblings;
        try (final Stream<Path> st = Files.list(parent)) {
            siblings = st.toList();
        }
        if (siblings.isEmpty()) {
            Files.delete(parent);
            this.deleteEmptyParents(parent);
        }
    }
}
