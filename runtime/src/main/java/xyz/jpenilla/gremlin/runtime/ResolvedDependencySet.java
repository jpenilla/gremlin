package xyz.jpenilla.gremlin.runtime;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record ResolvedDependencySet(Map<Dependency, Path> map) {
    public Set<Path> files() {
        return Set.copyOf(this.map.values());
    }
}
