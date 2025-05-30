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

import org.gradle.api.provider.Property
import org.gradle.api.tasks.AbstractCopyTask
import org.gradle.api.tasks.TaskProvider

abstract class GremlinExtension {
    abstract val defaultJarRelocatorDependencies: Property<Boolean>
    abstract val addGremlinRuntimeToCompileClasspath: Property<Boolean>
    abstract val addGremlinRuntimeToRuntimeClasspath: Property<Boolean>

    init {
        init()
    }

    private fun init() {
        defaultJarRelocatorDependencies.convention(true)
        addGremlinRuntimeToCompileClasspath.convention(true)
        addGremlinRuntimeToRuntimeClasspath.convention(true)
    }

    fun nestJars(
        prepareNestedJars: TaskProvider<out PrepareNestedJars>,
        into: TaskProvider<out AbstractCopyTask>
    ) {
        into.configure {
            from(prepareNestedJars.flatMap { it.outputDir }) {
                into("nested-jars")
            }
        }
    }
}
