package xyz.jpenilla.gremlin.runtime.platformsupport;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import org.jspecify.annotations.NullMarked;
import xyz.jpenilla.gremlin.runtime.DependencyDownloader;
import xyz.jpenilla.gremlin.runtime.DependencySet;

/**
 * Paper {@link PluginLoader} that automatically loads dependencies using
 * {@link DependencySet#readDefault(ClassLoader)} and adds them to
 * the plugin classpath.
 *
 * <p>This is provided as a convenience for the common case, if custom behavior
 * is required you will need to implement {@link PluginLoader} yourself.</p>
 */
@NullMarked
public final class DefaultsPaperPluginLoader implements PluginLoader {
    @Override
    public void classloader(final PluginClasspathBuilder classpath) {
        final DependencySet deps = DependencySet.readDefault(this.getClass().getClassLoader());
        final DependencyDownloader downloader = new DependencyDownloader(
            classpath.getContext().getLogger(),
            classpath.getContext().getDataDirectory().resolve("libraries"),
            deps
        );
        new PaperClasspathAppender(classpath).append(downloader.resolve());
    }
}
