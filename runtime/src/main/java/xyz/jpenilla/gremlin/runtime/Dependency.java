package xyz.jpenilla.gremlin.runtime;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
record Dependency(String group, String name, String version, @Nullable String classifier, String sha256) {}
