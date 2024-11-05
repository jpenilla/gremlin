plugins {
    id("runtime-conventions")
}

description = "gremlin runtime"

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly(libs.jarRelocator)
    compileOnly(libs.slf4j.api)
    compileOnly(libs.log4j.api)
    compileOnlyApi(libs.jspecifyAnnotations)

    compileOnly(libs.bundles.platformSupport)
}

tasks.compileJava {
    // we don't support Java serialization
    options.compilerArgs.add("-Xlint:-serial")
}
