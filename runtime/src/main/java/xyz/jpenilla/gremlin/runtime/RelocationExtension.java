/*
 * gremlin
 *
 * Copyright (c) 2025 Jason Penilla
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
