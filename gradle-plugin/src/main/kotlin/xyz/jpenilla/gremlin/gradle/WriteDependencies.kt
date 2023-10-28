package xyz.jpenilla.gremlin.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.DependencyResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class WriteDependencies : DefaultTask() {
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

    @get:Input
    abstract val outputFileName: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val repos: ListProperty<String>

    @get:Input
    abstract val transitive: Property<Boolean>

    @get:Input
    abstract val relocations: ListProperty<String>

    init {
        init()
    }

    private fun init() {
        transitive.convention(true)
    }

    fun relocate(from: String, to: String) {
        relocations.add("$from $to")
    }

    @TaskAction
    fun run() {
        val out = StringBuilder()
        val outputFile = outputDir.get().file(outputFileName.get()).asFile

        out.sectionHeader("repos")
        for (repo in repos.get()) {
            out.append(repo).append("\n")
        }
        out.sectionEnd()

        out.sectionHeader("deps")
        for (dependency in deps(tree.get())) {
            out.append(dependencyLine(dependency))
        }
        out.sectionEnd()

        if (relocations.isPresent && relocations.get().isNotEmpty()) {
            out.sectionHeader("relocation")

            for (dependency in deps(relocTree.get())) {
                out.append("dep ").append(dependencyLine(dependency))
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

    protected fun dependencyLine(dependency: ResolvedDependencyResult): String {
        val id = dependency.resolvedVariant.owner as ModuleComponentIdentifier
        val file = files.files.single { it.name.equals("${id.module}-${id.version}.jar") }
        return "${id.displayName} ${file.toPath().hashFile(HashingAlgorithm.SHA256).asHexString()}\n"
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
            if (transitive.get()) {
                addFrom(dependency.selected.dependencies)
            }
        }
    }
}
