package xyz.jpenilla.gremlin.runtime;

import java.util.Comparator;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
record Dependency(
    String group,
    String name,
    String version,
    @Nullable String classifier,
    @Nullable String extension,
    String sha256
) implements Comparable<Dependency> {
    private static final Comparator<Dependency> COMPARATOR =
        Comparator.comparing(Dependency::group)
            .thenComparing(Dependency::name)
            .thenComparing(Dependency::version)
            .thenComparing(dependency -> dependency.classifier() == null ? "" : dependency.classifier())
            .thenComparing(dependency -> dependency.extension() == null ? "" : dependency.extension())
            .thenComparing(Dependency::sha256);

    @Override
    public int compareTo(final Dependency o) {
        return COMPARATOR.compare(this, o);
    }

    public static Dependency parse(final String notation, final String sha256) {
        final String[] parts = notation.split(":");
        final String[] extParts = parts[parts.length - 1].split("@");

        return new Dependency(
            parts[0],
            parts[1],
            parts[2],
            parts.length == 4 ? parts[3] : null,
            extParts.length == 2 ? extParts[1] : null,
            sha256
        );
    }
}
