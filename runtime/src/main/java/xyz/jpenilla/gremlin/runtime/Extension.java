package xyz.jpenilla.gremlin.runtime;

import java.util.List;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface Extension<S> {
    S parseConfig(List<String> lines);

    List<Dependency> dependencies(S config);

    String processorName();
}
