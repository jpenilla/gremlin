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

import java.nio.file.Path;
import java.util.Collection;
import org.jspecify.annotations.NullMarked;
import xyz.jpenilla.gremlin.runtime.platformsupport.FabricClasspathAppender;
import xyz.jpenilla.gremlin.runtime.platformsupport.PaperClasspathAppender;
import xyz.jpenilla.gremlin.runtime.platformsupport.VelocityClasspathAppender;

/**
 * Helper for appending jar {@link Path Paths} to the
 * classpath.
 *
 * @see PaperClasspathAppender
 * @see VelocityClasspathAppender
 * @see FabricClasspathAppender
 */
@FunctionalInterface
@NullMarked
public interface ClasspathAppender {
    void append(Path path);

    default void append(final Collection<Path> paths) {
        for (final Path path : paths) {
            this.append(path);
        }
    }
}
