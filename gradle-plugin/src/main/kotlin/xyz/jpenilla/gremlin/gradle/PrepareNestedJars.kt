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

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.nio.file.Files
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.writeText

@OptIn(ExperimentalPathApi::class)
abstract class PrepareNestedJars : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:InputFiles
    abstract val nestedJars: ConfigurableFileCollection

    init {
        init()
    }

    private fun init() {
        outputDir.convention(project.layout.buildDirectory.dir("nested-jars/$name"))
    }

    @TaskAction
    fun run() {
        val outputPath = outputDir.path
        outputPath.deleteRecursively()
        Files.createDirectories(outputPath)

        val index = mutableListOf<String>()
        nestedJars.files.forEach { jar ->
            Files.copy(jar.toPath(), outputPath.resolve(jar.name))
            index += jar.name
        }
        outputPath.resolve("index.txt").writeText(index.joinToString("\n"))
    }
}
