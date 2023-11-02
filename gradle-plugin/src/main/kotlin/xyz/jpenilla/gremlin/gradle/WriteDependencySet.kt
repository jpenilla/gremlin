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
package xyz.jpenilla.gremlin.gradle

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.internal.artifacts.repositories.resolver.MavenUniqueSnapshotComponentIdentifier
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactIdentifier
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier
import org.gradle.internal.component.model.IvyArtifactName
import java.util.function.Function
import javax.inject.Inject

abstract class WriteDependencySet : DefaultTask() {
    companion object {
        protected const val SECTION_END = "__end__\n"
    }

    @get:Internal
    abstract val artifacts: SetProperty<ResolvedArtifactResult>

    @get:InputFiles
    abstract val artifactFiles: ConfigurableFileCollection

    @get:Internal
    abstract val relocArtifacts: SetProperty<ResolvedArtifactResult>

    @get:InputFiles
    @get:Optional
    abstract val relocArtifactFiles: ConfigurableFileCollection

    @get:Input
    abstract val outputFileName: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val repos: ListProperty<String>

    @get:Nested
    abstract val relocations: NamedDomainObjectContainer<Relocation>

    @get:Inject
    abstract val layout: ProjectLayout

    interface Relocation : Named {
        @Input
        override fun getName(): String

        @get:Input
        val from: Property<String>

        @get:Input
        val to: Property<String>

        @get:Input
        val includes: SetProperty<String>

        @get:Input
        val excludes: SetProperty<String>
    }

    init {
        init()
    }

    private fun init() {
        outputDir.convention(layout.buildDirectory.dir("generated/gremlin/$name"))
    }

    @JvmOverloads
    fun relocate(from: String, to: String, configure: Action<Relocation>? = null): Relocation =
        relocations.create(relocations.size.toString()) {
            this.from.set(from)
            this.to.set(to)
            configure?.execute(this)
        }

    fun configuration(configuration: Configuration) {
        artifacts.set(configuration.incoming.artifacts)
        artifactFiles.setFrom(configuration)
    }

    fun configuration(configuration: Provider<Configuration>) {
        artifacts.set(configuration.map { it.incoming.artifacts })
        artifactFiles.setFrom(configuration)
    }

    fun relocationConfiguration(configuration: Configuration) {
        relocArtifacts.set(configuration.incoming.artifacts)
        relocArtifactFiles.setFrom(configuration)
    }

    fun relocationConfiguration(configuration: Provider<Configuration>) {
        relocArtifacts.set(configuration.map { it.incoming.artifacts })
        relocArtifactFiles.setFrom(configuration)
    }

    @TaskAction
    fun run() {
        val out = StringBuilder()
        val outputFile = outputDir.get().file(outputFileName.get()).asFile

        out.sectionHeader("repos")
        for (repo in repos.get()) {
            out.append(repo)
            if (!repo.endsWith('/')) {
                out.append('/')
            }
            out.append("\n")
        }
        out.sectionEnd()

        out.sectionHeader("deps")
        for (dependency in artifacts.sorted()) {
            out.append(dependencyLine(dependency))
        }
        out.sectionEnd()

        if (relocations.isNotEmpty()) {
            out.sectionHeader("relocation")

            for (dependency in relocArtifacts.sorted()) {
                out.append("dep ").append(dependencyLine(dependency))
            }

            for (r in relocations) {
                out.append(r.from.get()).append(' ').append(r.to.get())
                for (inc in r.includes.get()) {
                    out.append(' ').append(':').append(inc)
                }
                for (exc in r.excludes.get()) {
                    out.append(' ').append('-').append(exc)
                }
                out.append("\n")
            }

            out.sectionEnd()
        }

        outputFile.parentFile.mkdirs()
        outputFile.delete()
        touchOutput(out)
        outputFile.writeText(out.toString())
    }

    protected open fun touchOutput(out: StringBuilder) {
    }

    protected fun dependencyLine(artifact: ResolvedArtifactResult, appendNewline: Boolean = true): String {
        val artifactId = artifact.id
        val componentId = artifactId.componentIdentifier as? ModuleComponentIdentifier ?: return ""

        val ivyName = when (artifactId) {
            is DefaultModuleComponentArtifactIdentifier -> artifactId.name
            is ComponentFileArtifactIdentifier -> artifactId.rawFileName as? IvyArtifactName
            else -> return ""
        }
        val classifier = ivyName?.classifier?.takeIf { it.isNotBlank() }
        val ext = ivyName?.extension?.takeIf { it.isNotBlank() && it != "jar" }

        var notation = if (componentId is MavenUniqueSnapshotComponentIdentifier) {
            "${componentId.group}:${componentId.module}:${componentId.timestampedVersion}"
        } else {
            "${componentId.group}:${componentId.module}:${componentId.version}"
        }
        if (classifier != null) {
            notation = "$notation:$classifier"
        }
        if (ext != null) {
            notation = "$notation@$ext"
        }

        val hashString = artifact.file.toPath().hashFile(HashingAlgorithm.SHA256).asHexString()

        return "$notation $hashString" + if (appendNewline) "\n" else ""
    }

    protected fun StringBuilder.sectionHeader(name: String) {
        append("__${name}__\n")
    }

    protected fun StringBuilder.sectionEnd() {
        append(SECTION_END)
    }

    protected fun Provider<Set<ResolvedArtifactResult>>.sorted(): List<ResolvedArtifactResult> = get().sortedWith(
        Comparator.comparing<ResolvedArtifactResult, String> { it.id.componentIdentifier.displayName }
            .thenComparing(Function { it.file.name })
    )
}
