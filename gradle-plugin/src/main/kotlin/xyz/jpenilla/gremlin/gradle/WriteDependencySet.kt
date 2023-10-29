package xyz.jpenilla.gremlin.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

abstract class WriteDependencySet : DefaultTask() {
    companion object {
        protected const val SECTION_END = "__end__\n"
    }

    @get:Input
    abstract val tree: Property<ResolvedComponentResult>

    @get:Input
    @get:Optional
    abstract val relocTree: Property<ResolvedComponentResult>

    @get:InputFiles
    abstract val files: ConfigurableFileCollection

    @get:InputFiles
    abstract val relocFiles: ConfigurableFileCollection

    @get:Input
    abstract val outputFileName: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val repos: ListProperty<String>

    @get:Input
    abstract val relocations: ListProperty<String>

    @get:Inject
    abstract val layout: ProjectLayout

    init {
        init()
    }

    private fun init() {
        outputDir.convention(layout.buildDirectory.dir("generated/gremlin/$name"))
    }

    fun relocate(from: String, to: String) {
        relocations.add("$from $to")
    }

    fun configuration(configuration: Configuration) {
        tree.set(configuration.incoming.resolutionResult.rootComponent)
        files.setFrom(configuration)
    }

    fun configuration(configuration: Provider<Configuration>) {
        tree.set(configuration.flatMap { it.incoming.resolutionResult.rootComponent })
        files.setFrom(configuration)
    }

    fun relocationConfiguration(configuration: Configuration) {
        relocTree.set(configuration.incoming.resolutionResult.rootComponent)
        relocFiles.setFrom(configuration)
    }

    fun relocationConfiguration(configuration: Provider<Configuration>) {
        relocTree.set(configuration.flatMap { it.incoming.resolutionResult.rootComponent })
        relocFiles.setFrom(configuration)
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
        for (dependency in deps(tree.get())) {
            out.append(dependencyLines(files, dependency))
        }
        out.sectionEnd()

        if (relocations.isPresent && relocations.get().isNotEmpty()) {
            out.sectionHeader("relocation")

            for (dependency in deps(relocTree.get())) {
                out.append(dependencyLines(relocFiles, dependency, linePrefix = "dep "))
            }

            for (r in relocations.get().sorted()) {
                out.append(r).append("\n")
            }

            out.sectionEnd()
        }

        outputFile.parentFile.mkdirs()
        outputFile.delete()
        touchOutput(out)
        outputFile.writeText(out.toString())
    }

    open fun touchOutput(out: StringBuilder) {
    }

    protected fun dependencyLines(files: ConfigurableFileCollection, dependency: ResolvedDependencyResult, linePrefix: String = ""): String {
        val id = dependency.resolvedVariant.owner as ModuleComponentIdentifier
        val filter = files.files.filter { it.name.startsWith("${id.module}-${id.version}") && it.name.endsWith(".jar") }
        val s = StringBuilder()
        for (file in filter) {
            val displayName = if (id.version.endsWith("-SNAPSHOT")) {
                id.displayName.replace("-SNAPSHOT:", "-")
            } else {
                id.displayName
            }
            val classifier = file.name.substringAfter("${id.module}-${id.version}-", missingDelimiterValue = "").substringBefore(".jar")
            val notation = if (classifier.isNotBlank()) {
                "$displayName:$classifier"
            } else {
                displayName
            }
            s.append("$linePrefix$notation ${file.toPath().hashFile(HashingAlgorithm.SHA256).asHexString()}\n")
        }
        return s.toString()
    }

    protected fun StringBuilder.sectionHeader(name: String) {
        append("__${name}__\n")
    }

    protected fun StringBuilder.sectionEnd() {
        append(SECTION_END)
    }

    private fun deps(c: ResolvedComponentResult): List<ResolvedDependencyResult> {
        val set = mutableSetOf<ResolvedDependencyResult>()
        set.addFrom(c.dependencies)
        return set.associateBy { it.resolvedVariant.owner.displayName }
            .map { it.value }
            .sortedBy { it.resolvedVariant.owner.displayName }
    }

    private fun MutableSet<ResolvedDependencyResult>.addFrom(dependencies: Set<DependencyResult>) {
        for (dependency in dependencies) {
            dependency as ResolvedDependencyResult
            if (dependency.resolvedVariant.attributes.toString().contains("org.gradle.category=platform")) {
                continue
            }
            add(dependency)

            addFrom(dependency.selected.dependencies)
        }
    }
}
