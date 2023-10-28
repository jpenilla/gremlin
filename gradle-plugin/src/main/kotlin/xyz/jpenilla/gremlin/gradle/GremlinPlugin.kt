package xyz.jpenilla.gremlin.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.GradleInternal
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

class GremlinPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val ext = target.extensions.create("gremlin", GremlinExtension::class)

        val runtimeDownload = target.configurations.register("runtimeDownload") {
            isCanBeResolved = true
            isCanBeConsumed = false
            attributes {
                attribute(Category.CATEGORY_ATTRIBUTE, target.objects.named(Category.LIBRARY))
                attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, target.objects.named(LibraryElements.JAR))
                attribute(Bundling.BUNDLING_ATTRIBUTE, target.objects.named(Bundling.EXTERNAL))
                attribute(Usage.USAGE_ATTRIBUTE, target.objects.named(Usage.JAVA_RUNTIME))
            }
        }

        val jarRelocatorRuntime = target.configurations.register("jarRelocatorRuntime") {
            isCanBeResolved = true
            isCanBeConsumed = false
            attributes {
                attribute(Category.CATEGORY_ATTRIBUTE, target.objects.named(Category.LIBRARY))
                attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, target.objects.named(LibraryElements.JAR))
                attribute(Bundling.BUNDLING_ATTRIBUTE, target.objects.named(Bundling.EXTERNAL))
                attribute(Usage.USAGE_ATTRIBUTE, target.objects.named(Usage.JAVA_RUNTIME))
            }
        }

        val writeDependencies = target.tasks.register("writeDependencies", WriteDependencies::class) {
            tree.convention(runtimeDownload.flatMap { it.incoming.resolutionResult.rootComponent })
            relocTree.convention(jarRelocatorRuntime.flatMap { it.incoming.resolutionResult.rootComponent })
            files.from(runtimeDownload, jarRelocatorRuntime)
            outputFileName.convention("dependencies.txt")
            outputDir.convention(target.layout.buildDirectory.dir("generated/writeDependencies"))
        }

        target.extensions.getByType<JavaPluginExtension>().apply {
            sourceSets.named("main") {
                resources {
                    srcDir(writeDependencies)
                }
            }
        }

        target.afterEvaluate {
            if (ext.defaultJarRelocatorDependencies.get()) {
                target.dependencies {
                    jarRelocatorRuntime.name("me.lucko:jar-relocator:1.7")
                    jarRelocatorRuntime.name("org.ow2.asm:asm-commons:9.6")
                    jarRelocatorRuntime.name("org.ow2.asm:asm:9.6")
                }
            }

            val defaultRepos = defaultRepos()
            writeDependencies {
                repos.convention(defaultRepos)
            }
        }
    }

    private fun Project.defaultRepos(): List<String> {
        if (repositories.isNotEmpty()) {
            return repositories.mapNotNull { (it as? MavenArtifactRepository)?.url.toString() }
        }
        return settings.dependencyResolutionManagement.repositories.mapNotNull { (it as? MavenArtifactRepository)?.url.toString() }
    }

    private val Project.settings: Settings
        get() = (gradle as GradleInternal).settings
}
