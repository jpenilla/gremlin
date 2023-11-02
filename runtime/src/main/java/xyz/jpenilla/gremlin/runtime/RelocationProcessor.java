/*
 * gremlin
 *
 * Copyright (c) 2023 Jason Penilla
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

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import me.lucko.jarrelocator.JarRelocator;
import me.lucko.jarrelocator.Relocation;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class RelocationProcessor implements JarProcessor {
    private final List<Relocation> relocations;
    private final String cacheKey;

    public RelocationProcessor(final RelocationExtension.Config config) {
        if (config.relocations().isEmpty()) {
            throw new IllegalStateException(this.getClass().getSimpleName() + " created without any relocations");
        }

        this.relocations = config.relocations().stream().map(line -> {
            final String[] split = line.split(" ");

            final Set<String> includes = new HashSet<>();
            final Set<String> excludes = new HashSet<>();
            if (split.length > 2) {
                for (int i = 2; i < split.length; i++) {
                    final String includeOrExclude = split[i];
                    switch (includeOrExclude.charAt(0)) {
                        case ':' -> includes.add(includeOrExclude.substring(1));
                        case '-' -> excludes.add(includeOrExclude.substring(1));
                        default -> throw new IllegalStateException("Invalid relocation '" + line + "'");
                    }
                }
            }

            return new Relocation(split[0], split[1], includes, excludes);
        }).toList();

        // Only include relocations in cache key, we assume that changes to the classpath/deps will mainly
        // be ASM updates for new Java versions, in which case any relocation that would have a different
        // outcome would have previously failed and will run again anyway.
        this.cacheKey = String.join(";", config.relocations());
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
