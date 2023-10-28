plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    alias(libs.plugins.indraGradlePublishing)
    alias(libs.plugins.gradlePluginPublish)
    alias(libs.plugins.blossom)
}

repositories {
    mavenCentral()
}

gradlePlugin {
    val plugin by plugins.creating {
        id = "xyz.jpenilla.gremlin-gradle"
        implementationClass = "xyz.jpenilla.gremlin.gradle.GremlinPlugin"
    }
}

tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class) {
    kotlinOptions.jvmTarget = "11"
}

indra {
    javaVersions {
        target(11)
        strictVersions(true)
    }
}

val jarRelocatorDefaultRuntime: Configuration by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

dependencies {
    jarRelocatorDefaultRuntime(libs.bundles.jarRelocatorDefaultRuntime)
}

sourceSets {
    main {
        blossom {
            kotlinSources {
                properties.put("jarRelocatorDefaultRuntime", provider {
                    jarRelocatorDefaultRuntime.incoming.resolutionResult.root.dependencies
                        .map { (it as ResolvedDependencyResult).resolvedVariant.owner.displayName }
                })
                properties.put("gremlinVer", project.version)
            }
        }
    }
}
