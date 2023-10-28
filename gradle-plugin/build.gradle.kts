plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    alias(libs.plugins.indraGradlePublishing)
    id("com.gradle.plugin-publish") version "1.2.1"
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
