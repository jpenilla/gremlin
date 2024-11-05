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

import java.util.logging.Level;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@ApiStatus.NonExtendable
@NullMarked
public interface GremlinLogger {
    void info(String message, @Nullable Throwable throwable);

    default void info(final String message) {
        this.info(message, null);
    }

    void debug(String message, @Nullable Throwable throwable);

    default void debug(final String message) {
        this.debug(message, null);
    }

    void warn(String message, @Nullable Throwable throwable);

    default void warn(final String message) {
        this.warn(message, null);
    }

    void error(String message, @Nullable Throwable throwable);

    default void error(final String message) {
        this.error(message, null);
    }

    void trace(String message, @Nullable Throwable throwable);

    default void trace(final String message) {
        this.trace(message, null);
    }

    static GremlinLogger slf4j(final org.slf4j.Logger logger) {
        return new GremlinLogger() {
            @Override
            public void info(final String message, final @Nullable Throwable throwable) {
                logger.info(message, throwable);
            }

            @Override
            public void debug(final String message, final @Nullable Throwable throwable) {
                logger.debug(message, throwable);
            }

            @Override
            public void warn(final String message, final @Nullable Throwable throwable) {
                logger.warn(message, throwable);
            }

            @Override
            public void error(final String message, final @Nullable Throwable throwable) {
                logger.error(message, throwable);
            }

            @Override
            public void trace(final String message, final @Nullable Throwable throwable) {
                logger.trace(message, throwable);
            }
        };
    }

    static GremlinLogger log4j(final org.apache.logging.log4j.Logger logger) {
        return new GremlinLogger() {
            @Override
            public void info(final String message, final @Nullable Throwable throwable) {
                logger.info(message, throwable);
            }

            @Override
            public void debug(final String message, final @Nullable Throwable throwable) {
                logger.debug(message, throwable);
            }

            @Override
            public void warn(final String message, final @Nullable Throwable throwable) {
                logger.warn(message, throwable);
            }

            @Override
            public void error(final String message, final @Nullable Throwable throwable) {
                logger.error(message, throwable);
            }

            @Override
            public void trace(final String message, final @Nullable Throwable throwable) {
                logger.trace(message, throwable);
            }
        };
    }

    static GremlinLogger jul(final java.util.logging.Logger logger) {
        return new GremlinLogger() {
            @Override
            public void info(final String message, final @Nullable Throwable throwable) {
                logger.log(Level.INFO, message, throwable);
            }

            @Override
            public void debug(final String message, final @Nullable Throwable throwable) {
                logger.log(Level.FINE, message, throwable);
            }

            @Override
            public void warn(final String message, final @Nullable Throwable throwable) {
                logger.log(Level.WARNING, message, throwable);
            }

            @Override
            public void error(final String message, final @Nullable Throwable throwable) {
                logger.log(Level.SEVERE, message, throwable);
            }

            @Override
            public void trace(final String message, final @Nullable Throwable throwable) {
                logger.log(Level.FINEST, message, throwable);
            }
        };
    }
}
