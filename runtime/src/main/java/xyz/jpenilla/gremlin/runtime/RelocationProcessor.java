package xyz.jpenilla.gremlin.runtime;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import me.lucko.jarrelocator.JarRelocator;
import me.lucko.jarrelocator.Relocation;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class RelocationProcessor implements JarProcessor {
    private final List<Relocation> relocations;

    public RelocationProcessor(final RelocationExtension.Config config) {
        this.relocations = config.relocations().stream().map(line -> {
            final String[] split = line.split(" ");
            return new Relocation(split[0], split[1]);
        }).toList();
    }

    @Override
    public void process(final Path input, final Path output) throws IOException {
        final JarRelocator relocator = new JarRelocator(
            input.toFile(),
            output.toFile(),
            this.relocations
        );
        relocator.run();
    }
}
