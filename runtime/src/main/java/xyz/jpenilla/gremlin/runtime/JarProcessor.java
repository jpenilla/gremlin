package xyz.jpenilla.gremlin.runtime;

import java.io.IOException;
import java.nio.file.Path;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface JarProcessor {
    void process(Path input, Path output) throws IOException;
}
