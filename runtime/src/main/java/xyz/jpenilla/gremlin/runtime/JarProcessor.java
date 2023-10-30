package xyz.jpenilla.gremlin.runtime;

import java.io.IOException;
import java.nio.file.Path;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public interface JarProcessor {
    @Nullable String cacheKey();

    void process(Path input, Path output) throws IOException;
}
