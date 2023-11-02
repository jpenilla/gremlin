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
}
