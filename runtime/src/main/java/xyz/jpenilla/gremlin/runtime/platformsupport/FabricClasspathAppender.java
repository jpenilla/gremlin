package xyz.jpenilla.gremlin.runtime.platformsupport;

import java.net.MalformedURLException;
import java.nio.file.Path;
import net.fabricmc.loader.launch.common.FabricLauncherBase;
import org.jspecify.annotations.NullMarked;
import xyz.jpenilla.gremlin.runtime.ClasspathAppender;
import xyz.jpenilla.gremlin.runtime.util.Util;

@NullMarked
public final class FabricClasspathAppender implements ClasspathAppender {
    @Override
    @SuppressWarnings("deprecation")
    public void append(final Path path) {
        try {
            FabricLauncherBase.getLauncher().propose(path.toUri().toURL());
        } catch (final MalformedURLException ex) {
            throw Util.rethrow(ex);
        }
    }
}
