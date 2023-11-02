package xyz.jpenilla.gremlin.runtime;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

@NullMarked
public record ResolvedDependencySet(Map<Dependency, Path> map) {
    public Set<Path> files() {
        return Set.copyOf(this.map.values());
    }

    public Set<Path> jarFiles() {
        final Set<Path> jars = new HashSet<>();
        this.map.forEach((dependency, path) -> {
            if (dependency.extension() == null || dependency.extension().equals("jar")) {
                jars.add(path);
            }
        });
        return Collections.unmodifiableSet(jars);
    }
}
