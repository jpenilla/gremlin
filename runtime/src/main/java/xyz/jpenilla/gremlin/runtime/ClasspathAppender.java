package xyz.jpenilla.gremlin.runtime;

import java.nio.file.Path;
import java.util.Collection;
import xyz.jpenilla.gremlin.runtime.platformsupport.FabricClasspathAppender;
import xyz.jpenilla.gremlin.runtime.platformsupport.PaperClasspathAppender;
import xyz.jpenilla.gremlin.runtime.platformsupport.VelocityClasspathAppender;

/**
 * Helper for appending jar {@link Path Paths} to the
 * classpath.
 *
 * @see PaperClasspathAppender
 * @see VelocityClasspathAppender
 * @see FabricClasspathAppender
 */
@FunctionalInterface
public interface ClasspathAppender {
    void append(Path path);

    default void append(final Collection<Path> paths) {
        for (final Path path : paths) {
            this.append(path);
        }
    }
}
