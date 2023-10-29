package xyz.jpenilla.gremlin.runtime;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class RelocationExtension implements Extension<RelocationExtension.Config> {
    public record Config(List<String> relocations, List<Dependency> deps) {}

    @Override
    public Config parseConfig(final List<String> lines) {
        final List<String> reloc = new ArrayList<>();
        final List<Dependency> deps = new ArrayList<>();
        for (final String line : lines) {
            if (line.startsWith("dep ")) {
                final String[] split = line.split(" ");
                deps.add(Dependency.parse(split[1], split[2]));
            } else {
                reloc.add(line);
            }
        }
        return new Config(reloc, deps);
    }

    @Override
    public List<Dependency> dependencies(final Config config) {
        return config.deps();
    }

    @Override
    public String processorName() {
        return "xyz.jpenilla.gremlin.runtime.RelocationProcessor";
    }
}
