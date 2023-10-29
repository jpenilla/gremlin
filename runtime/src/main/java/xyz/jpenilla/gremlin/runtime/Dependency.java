package xyz.jpenilla.gremlin.runtime;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
record Dependency(String group, String name, String version, @Nullable String classifier, String sha256) {
    public static Dependency parse(final String notation, final String sha256) {
        final String[] parts = notation.split(":");
        return new Dependency(
            parts[0],
            parts[1],
            parts[2],
            parts.length == 4 ? parts[3] : null,
            sha256
        );
    }
}
