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

import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.attributes
import javax.inject.Inject

abstract class GremlinJar : Jar() {
    @get:InputFiles
    abstract val gremlinRuntime: ConfigurableFileCollection

    @get:InputFiles
    abstract val nestedJars: ConfigurableFileCollection

    @get:InputFile
    abstract val nestedJarsIndex: RegularFileProperty

    @get:Inject
    abstract val archiveOps: ArchiveOperations

    @get:Input
    @get:Optional
    abstract val mainClass: Property<String>

    init {
        manifest.attributes(
            "Main-Class" to "xyz.jpenilla.gremlin.runtime.GremlinBootstrap",
        )
        val runtime = gremlinRuntime.elements.map {
            it.map { e ->
                archiveOps.zipTree(e)
            }
        }
        from(runtime) {
            exclude("META-INF/*")
        }
        from(nestedJars) {
            into("nested-jars")
        }
        from(nestedJarsIndex) {
            rename { "nested-jars/index.txt" }
        }
    }

    override fun copy() {
        manifest.attributes(
            "Gremlin-Main-Class" to mainClass.get(),
        )
        super.copy()
    }
}
