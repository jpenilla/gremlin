import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("base-conventions")
    `java-gradle-plugin`
    `kotlin-dsl`
    alias(libs.plugins.indra.publishing.gradlePlugin)
    alias(libs.plugins.gradle.pluginPublish)
    alias(libs.plugins.blossom)
}

val jarRelocatorDefaultRuntime: Configuration by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

repositories {
    mavenCentral {
        content {
            onlyForConfigurations(jarRelocatorDefaultRuntime.name)
        }
    }
    gradlePluginPortal()
}

dependencies {
    compileOnly(libs.shadow)

    jarRelocatorDefaultRuntime(libs.bundles.jarRelocatorDefaultRuntime)
}

indraPluginPublishing {
    website("https://github.com/jpenilla/gremlin")
    plugin(
        "gremlin-gradle",
        "xyz.jpenilla.gremlin.gradle.GremlinPlugin",
        "Gremlin",
        "Export dependency sets to be resolved by xyz.jpenilla:gremlin-runtime",
        listOf("dependency", "gremlin")
    )
}

tasks.withType(KotlinCompile::class) {
    kotlinOptions.jvmTarget = "11"
}

indra {
    javaVersions {
        target(11)
        strictVersions(true)
    }
}

publishing {
    repositories.maven("https://repo.jpenilla.xyz/snapshots/") {
        name = "jmp"
        credentials(PasswordCredentials::class)
    }
}

sourceSets.main {
    blossom.kotlinSources {
        properties.put("jarRelocatorDefaultRuntime", provider {
            jarRelocatorDefaultRuntime.incoming.resolutionResult.root.dependencies
                .map { (it as ResolvedDependencyResult).resolvedVariant.owner.displayName }
        })
        properties.put("gremlinVer", project.version)
    }
}
