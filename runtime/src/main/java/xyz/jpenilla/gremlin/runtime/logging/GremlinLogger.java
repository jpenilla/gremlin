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
}
