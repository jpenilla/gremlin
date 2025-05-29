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
package xyz.jpenilla.gremlin.runtime.logging;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.jspecify.annotations.Nullable;

public final class JavaGremlinLogger implements GremlinLogger {
    private final Logger logger;

    public JavaGremlinLogger(final Logger logger) {
        this.logger = logger;
    }

    @Override
    public void info(final String message, final @Nullable Throwable throwable) {
        this.logger.log(Level.INFO, message, throwable);
    }

    @Override
    public void debug(final String message, final @Nullable Throwable throwable) {
        this.logger.log(Level.FINE, message, throwable);
    }

    @Override
    public void warn(final String message, final @Nullable Throwable throwable) {
        this.logger.log(Level.WARNING, message, throwable);
    }

    @Override
    public void error(final String message, final @Nullable Throwable throwable) {
        this.logger.log(Level.SEVERE, message, throwable);
    }

    @Override
    public void trace(final String message, final @Nullable Throwable throwable) {
        this.logger.log(Level.FINEST, message, throwable);
    }
}
