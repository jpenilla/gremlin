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
package xyz.jpenilla.gremlin.gradle

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.internal.artifacts.repositories.resolver.MavenUniqueSnapshotComponentIdentifier
import org.gradle.api.model.ObjectFactory
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
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactIdentifier
import org.gradle.kotlin.dsl.newInstance
import java.io.File
import javax.inject.Inject

@Suppress("LeakingThis")
abstract class WriteDependencySet : DefaultTask() {
    companion object {
        protected const val SECTION_END = "__end__\n"
    }

    @get:Inject
    abstract val objects: ObjectFactory

    @get:Inject
    abstract val layout: ProjectLayout

    @get:Nested
    val dependencies: Artifacts = objects.newInstance(Artifacts::class)

    @get:Input
    abstract val outputFileName: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val repos: ListProperty<String>

    @get:Nested
    abstract val relocations: NamedDomainObjectContainer<Relocation>

    @get:Nested
    val relocationDependencies: Artifacts = objects.newInstance(Artifacts::class)

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
        for (dependency in dependencies.artifacts()) {
            dependencyLine(dependency)?.let { out.append(it) }
        }
        out.sectionEnd()

        if (relocations.isNotEmpty()) {
            out.sectionHeader("relocation")

            for (dependency in relocationDependencies.artifacts()) {
                dependencyLine(dependency)?.let { out.append("dep ").append(it) }
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

    protected fun dependencyLine(artifact: Artifacts.Artifact, appendNewline: Boolean = true): String? {
        val artifactId = artifact.id
        val componentId = artifactId.componentIdentifier as? ModuleComponentIdentifier ?: return null

        val ivyName = when (artifactId) {
            is DefaultModuleComponentArtifactIdentifier -> artifactId.name
            else -> null
        }

        val notation = StringBuilder()
            .append(componentId.group)
            .append(':')
            .append(componentId.module)
            .append(':')

        val version = if (componentId is MavenUniqueSnapshotComponentIdentifier) {
            componentId.timestampedVersion
        } else {
            componentId.version
        }

        notation.append(version)

        val classifier = ivyName?.classifier?.takeIf { it.isNotBlank() }
        if (classifier != null) {
            notation.append(':').append(classifier)
        }

        val ext = ivyName?.extension
            ?: artifact.file.extension.takeIf { it.isNotBlank() }
            ?: error("File '${artifact.file.absolutePath}' does not have an extension?")

        notation.append('@').append(ext)

        val hashString = artifact.file.toPath().hashFile(HashingAlgorithm.SHA256).asHexString()

        return "$notation $hashString" + if (appendNewline) "\n" else ""
    }

    protected fun StringBuilder.sectionHeader(name: String) {
        append("__${name}__\n")
    }

    protected fun StringBuilder.sectionEnd() {
        append(SECTION_END)
    }

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

    abstract class Artifacts {
        data class Artifact(
            val id: ComponentArtifactIdentifier,
            val variant: ResolvedVariantResult,
            val file: File,
        )

        // We need to split the metadata and files to be compatible with configuration cache.

        @get:Internal
        abstract val componentArtifactIdentifiers: ListProperty<ComponentArtifactIdentifier>

        @get:Internal
        abstract val resolvedVariantResults: ListProperty<ResolvedVariantResult>

        @get:Internal
        abstract val filesInternal: ListProperty<File>

        /**
         * Ensure Gradle properly tracks our dependency on these files (and their metadata)
         */
        @get:InputFiles
        @get:Optional
        @get:PathSensitive(PathSensitivity.NONE)
        abstract val files: ConfigurableFileCollection

        fun artifacts(): List<Artifact> {
            val ids = componentArtifactIdentifiers.get()
            val variants = resolvedVariantResults.get()
            val files = filesInternal.get()
            if (setOf(ids.size, variants.size, files.size).size != 1) {
                throw IllegalStateException("Mismatch between artifact input list lengths (ids: ${ids.size}, variants: ${variants.size}, files: ${files.size})")
            }
            return ids.mapIndexed { idx, id -> Artifact(id, variants[idx], files[idx]) }
        }

        fun setFrom(configuration: NamedDomainObjectProvider<Configuration>) = setFrom(configuration.map { it.incoming.artifacts })

        fun setFrom(artifactCollection: Provider<ArtifactCollection>) {
            files.setFrom(artifactCollection.map { it.artifactFiles })
            val artifactsSorted = artifactCollection.flatMap {
                it.resolvedArtifacts.map { resolvedArtifacts ->
                    resolvedArtifacts.sortedWith(
                        Comparator.comparing<ResolvedArtifactResult, String> { artifact -> artifact.id.componentIdentifier.displayName }
                            .thenComparing { artifact -> artifact.file.name }
                    )
                }
            }
            componentArtifactIdentifiers.set(artifactsSorted.map { list -> list.map { it.id } })
            resolvedVariantResults.set(artifactsSorted.map { list -> list.map { it.variant } })
            filesInternal.set(artifactsSorted.map { list -> list.map { it.file } })
        }
    }
}
