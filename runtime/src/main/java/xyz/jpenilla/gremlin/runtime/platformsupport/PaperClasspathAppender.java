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
package xyz.jpenilla.gremlin.runtime.platformsupport;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.library.impl.JarLibrary;
import java.nio.file.Path;
import org.jspecify.annotations.NullMarked;
import xyz.jpenilla.gremlin.runtime.ClasspathAppender;

@NullMarked
public final class PaperClasspathAppender implements ClasspathAppender {
    private final PluginClasspathBuilder builder;

    public PaperClasspathAppender(final PluginClasspathBuilder builder) {
        this.builder = builder;
    }

    @Override
    public void append(final Path path) {
        this.builder.addLibrary(new JarLibrary(path));
    }
}
