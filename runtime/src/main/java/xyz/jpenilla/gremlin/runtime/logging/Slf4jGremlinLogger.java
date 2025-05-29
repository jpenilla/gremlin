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

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class Slf4jGremlinLogger implements GremlinLogger {
    private final Logger logger;

    public Slf4jGremlinLogger(final Logger logger) {
        this.logger = logger;
    }

    @Override
    public void info(final String message, final @Nullable Throwable throwable) {
        this.logger.info(message, throwable);
    }

    @Override
    public void debug(final String message, final @Nullable Throwable throwable) {
        this.logger.debug(message, throwable);
    }

    @Override
    public void warn(final String message, final @Nullable Throwable throwable) {
        this.logger.warn(message, throwable);
    }

    @Override
    public void error(final String message, final @Nullable Throwable throwable) {
        this.logger.error(message, throwable);
    }

    @Override
    public void trace(final String message, final @Nullable Throwable throwable) {
        this.logger.trace(message, throwable);
    }
}
