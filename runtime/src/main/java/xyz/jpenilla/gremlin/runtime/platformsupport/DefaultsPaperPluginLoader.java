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
import io.papermc.paper.plugin.loader.PluginLoader;
import org.jspecify.annotations.NullMarked;
import xyz.jpenilla.gremlin.runtime.DependencyCache;
import xyz.jpenilla.gremlin.runtime.DependencyResolver;
import xyz.jpenilla.gremlin.runtime.DependencySet;

/**
 * Paper {@link PluginLoader} that automatically loads dependencies using
 * {@link DependencySet#readDefault(ClassLoader)}, resolves them to
 * {@code plugins/<plugin_name>/libraries/} using {@link DependencyResolver},
 * and then adds them to the plugin classpath.
 *
 * <p>This is provided as a convenience for the common case, and isn't meant to
 * be flexible. If custom behavior is required, a custom {@link PluginLoader}
 * should be implemented.</p>
 */
@NullMarked
public final class DefaultsPaperPluginLoader implements PluginLoader {
    @Override
    public void classloader(final PluginClasspathBuilder classpath) {
        final DependencySet deps = DependencySet.readDefault(this.getClass().getClassLoader());
        final DependencyCache cache = new DependencyCache(classpath.getContext().getDataDirectory().resolve("libraries"));
        try (final DependencyResolver downloader = new DependencyResolver(classpath.getContext().getLogger())) {
            new PaperClasspathAppender(classpath).append(downloader.resolve(deps, cache).jarFiles());
        }
        cache.cleanup();
    }
}
