package xyz.jpenilla.gremlin.runtime.platformsupport;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.library.impl.JarLibrary;
import java.nio.file.Path;
import org.jspecify.annotations.NullMarked;
import xyz.jpenilla.gremlin.runtime.ClasspathAppender;

@NullMarked
public final class PaperClasspathAppender implements ClasspathAppender {
    private final PluginClasspathBuilder builder;

    public PaperClasspathAppender(final PluginClasspathBuilder builder) {
        this.builder = builder;
    }

    @Override
    public void append(final Path path) {
        this.builder.addLibrary(new JarLibrary(path));
    }
}
