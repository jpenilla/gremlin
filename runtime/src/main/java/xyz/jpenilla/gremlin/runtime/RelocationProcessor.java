package xyz.jpenilla.gremlin.runtime;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import me.lucko.jarrelocator.JarRelocator;
import me.lucko.jarrelocator.Relocation;
import org.jspecify.annotations.NullMarked;
import xyz.jpenilla.gremlin.runtime.util.HashingAlgorithm;
import xyz.jpenilla.gremlin.runtime.util.Util;

@NullMarked
public final class RelocationProcessor implements JarProcessor {
    private final List<Relocation> relocations;
    private final String cacheKey;

    public RelocationProcessor(final RelocationExtension.Config config) {
        if (config.relocations().isEmpty()) {
            throw new IllegalStateException(this.getClass().getSimpleName() + " created without any relocations");
        }
        final List<String> sorted = config.relocations().stream().sorted().toList();

        this.relocations = sorted.stream().map(line -> {
            final String[] split = line.split(" ");
            return new Relocation(split[0], split[1]);
        }).toList();

        try {
            // Only include relocations in cache key, we assume that changes to the classpath/deps will mainly
            // be ASM updates for new Java versions, in which case any relocation that would have a different
            // outcome would have previously failed and will run again anyway.
            this.cacheKey = HashingAlgorithm.SHA1.hashString(String.join(";", sorted)).asHexString();
        } catch (final IOException e) {
            throw Util.rethrow(e);
        }
    }

    @Override
    public String cacheKey() {
        return this.cacheKey;
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
