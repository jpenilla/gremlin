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
package xyz.jpenilla.gremlin.runtime.platformsupport;

import com.velocitypowered.api.proxy.ProxyServer;
import java.nio.file.Path;
import org.jspecify.annotations.NullMarked;
import xyz.jpenilla.gremlin.runtime.ClasspathAppender;

@NullMarked
public final class VelocityClasspathAppender implements ClasspathAppender {
    private final ProxyServer proxy;
    private final Object plugin;

    public VelocityClasspathAppender(final ProxyServer proxy, final Object plugin) {
        this.proxy = proxy;
        this.plugin = plugin;
    }

    @Override
    public void append(final Path path) {
        this.proxy.getPluginManager().addToClasspath(this.plugin, path);
    }
}
