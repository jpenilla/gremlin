/*
 * gremlin
 *
 * Copyright (c) 2024 Jason Penilla
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package xyz.jpenilla.gremlin.runtime;

import java.util.Comparator;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record Dependency(
    String group,
    String name,
    String version,
    @Nullable String classifier,
    String extension,
    String sha256
) implements Comparable<Dependency> {
    private static final Comparator<Dependency> COMPARATOR =
        Comparator.comparing(Dependency::group)
            .thenComparing(Dependency::name)
            .thenComparing(Dependency::version)
            .thenComparing(dependency -> dependency.classifier() == null ? "" : dependency.classifier())
            .thenComparing(Dependency::extension)
            .thenComparing(Dependency::sha256);

    @Override
    public int compareTo(final Dependency o) {
        return COMPARATOR.compare(this, o);
    }

    public static Dependency parse(final String notation, final String sha256) {
        final String[] parts = notation.split(":");
        final String[] extParts = parts[parts.length - 1].split("@");
        if (extParts.length == 2) {
            parts[parts.length - 1] = extParts[0];
        } else {
            throw new IllegalArgumentException("Incomplete notation '" + notation + "', missing extension.");
        }

        return new Dependency(
            parts[0],
            parts[1],
            parts[2],
            parts.length == 4 ? parts[3] : null,
            extParts[1],
            sha256
        );
    }
}
