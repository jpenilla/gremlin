package xyz.jpenilla.gremlin.runtime;

import org.jspecify.annotations.NullMarked;

@NullMarked
record Dependency(String group, String name, String version, String sha256) {}
