package xyz.jpenilla.gremlin.gradle

import org.gradle.api.provider.Property

abstract class GremlinExtension {
    abstract val defaultJarRelocatorDependencies: Property<Boolean>

    init {
        init()
    }

    private fun init() {
        defaultJarRelocatorDependencies.convention(true)
    }
}
