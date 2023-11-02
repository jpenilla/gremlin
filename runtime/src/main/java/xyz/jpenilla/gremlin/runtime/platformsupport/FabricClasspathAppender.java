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

import java.net.MalformedURLException;
import java.nio.file.Path;
import net.fabricmc.loader.launch.common.FabricLauncherBase;
import org.jspecify.annotations.NullMarked;
import xyz.jpenilla.gremlin.runtime.ClasspathAppender;
import xyz.jpenilla.gremlin.runtime.util.Util;

@NullMarked
public final class FabricClasspathAppender implements ClasspathAppender {
    @Override
    @SuppressWarnings("deprecation")
    public void append(final Path path) {
        try {
            FabricLauncherBase.getLauncher().propose(path.toUri().toURL());
        } catch (final MalformedURLException ex) {
            throw Util.rethrow(ex);
        }
    }
}
