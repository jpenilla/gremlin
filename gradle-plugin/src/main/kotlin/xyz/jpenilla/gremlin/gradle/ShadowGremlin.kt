package xyz.jpenilla.gremlin.gradle

import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import java.util.concurrent.ThreadLocalRandom

/**
 * Helpers for using [GremlinPlugin] with [ShadowPlugin].
 */
object ShadowGremlin {
    fun copyGremlinRelocations(
        from: TaskProvider<WriteDependencySet>,
        to: TaskProvider<ShadowJar>
    ) {
        val copy = from.flatMap { it.relocations }
        to.configure {
            inputs.property(
                "copiedRelocations${ThreadLocalRandom.current().nextInt()}".replace("-", ""),
                copy
            )
            doFirst {
                for (reloc in copy.get()) {
                    val s = reloc.split(" ")
                    relocate(this, s[0], s[1])
                }
            }
        }
    }

    fun copyShadowRelocations(
        from: TaskProvider<ShadowJar>,
        to: TaskProvider<WriteDependencySet>
    ) {
        val copy = from.map {
            it.relocators.mapNotNull { r ->
                (r as? SimpleRelocator)?.let { s ->
                    s.pattern + ' ' + s.shadedPattern
                }
            }
        }
        to.configure {
            inputs.property(
                "copiedRelocations${ThreadLocalRandom.current().nextInt()}".replace("-", ""),
                copy
            )
            relocations.addAll(copy)
        }
    }

    fun relocate(task: Task, from: String, to: String) {
        when (task) {
            is WriteDependencySet -> task.relocate(from, to)
            is ShadowJar -> task.relocate(from, to)
            else -> error("Expected ${ShadowJar::class.java.name} or ${WriteDependencySet::class.java.name}, not ${task::class.java.name}")
        }
    }
}
