package xyz.jpenilla.gremlin.runtime.platformsupport;

import com.velocitypowered.api.proxy.ProxyServer;
import java.nio.file.Path;
import org.jspecify.annotations.NullMarked;
import xyz.jpenilla.gremlin.runtime.ClasspathAppender;

@NullMarked
public final class VelocityClasspathAppender implements ClasspathAppender {
    private final ProxyServer proxy;
    private final Object plugin;

    public VelocityClasspathAppender(final ProxyServer proxy, final Object plugin) {
        this.proxy = proxy;
        this.plugin = plugin;
    }

    @Override
    public void append(final Path path) {
        this.proxy.getPluginManager().addToClasspath(this.plugin, path);
    }
}
