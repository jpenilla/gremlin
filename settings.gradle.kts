pluginManagement {
    includeBuild("build-logic")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "gremlin"

include("runtime")
findProject(":runtime")?.name = "gremlin-runtime"

include("gradle-plugin")
findProject(":gradle-plugin")?.name = "gremlin-gradle"
