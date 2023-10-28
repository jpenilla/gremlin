package xyz.jpenilla.gremlin.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.GradleInternal
import org.gradle.api.model.ObjectFactory
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
            makeResolvable()
            runtimeClasspathAttributes(target.objects)
        }

        val jarRelocatorRuntime = target.configurations.register("jarRelocatorRuntime") {
            makeResolvable()
            runtimeClasspathAttributes(target.objects)
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
                    for (notation in Dependencies.DEFAULT_JAR_RELOCATOR_RUNTIME) {
                        jarRelocatorRuntime.name(notation)
                    }
                }
            }

            val defaultRepos = defaultRepos()
            writeDependencies {
                repos.convention(defaultRepos)
            }
        }
    }

    private fun Configuration.makeResolvable() {
        isCanBeResolved = true
        isCanBeConsumed = false
    }

    private fun Configuration.runtimeClasspathAttributes(objects: ObjectFactory) {
        attributes {
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
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
