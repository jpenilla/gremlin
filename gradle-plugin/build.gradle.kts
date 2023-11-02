import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    alias(libs.plugins.indraGradlePublishing)
    alias(libs.plugins.gradlePluginPublish)
    alias(libs.plugins.blossom)
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

val jarRelocatorDefaultRuntime: Configuration by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
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
