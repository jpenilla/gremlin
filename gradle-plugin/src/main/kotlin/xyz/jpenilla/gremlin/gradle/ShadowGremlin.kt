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
package xyz.jpenilla.gremlin.gradle

import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Task

/**
 * Helpers for using [GremlinPlugin] with [ShadowPlugin].
 */
object ShadowGremlin {
    @JvmOverloads
    fun relocate(
        task: Task,
        from: String,
        to: String,
        includes: Set<String> = setOf(),
        excludes: Set<String> = setOf()
    ) {
        when (task) {
            is WriteDependencySet -> task.relocate(from, to) {
                this.includes.set(includes)
                this.excludes.set(excludes)
            }

            is ShadowJar -> task.relocate(from, to) {
                includes.forEach { include(it) }
                excludes.forEach { exclude(it) }
            }

            else -> error("Expected ${ShadowJar::class.java.name} or ${WriteDependencySet::class.java.name}, not ${task::class.java.name}")
        }
    }

    fun relocateWithPrefix(
        task: Task,
        prefix: String,
        vararg fromPackages: String
    ) {
        relocateWithPrefix(task, prefix, listOf(*fromPackages))
    }

    fun relocateWithPrefix(
        task: Task,
        prefix: String,
        fromPackages: Collection<String>
    ) {
        for (pkg in fromPackages) {
            relocate(task, pkg, "$prefix.$pkg")
        }
    }
}
