plugins {
    id("net.kyori.indra.publishing")
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly(libs.jarRelocator)
    compileOnlyApi(libs.slf4j.api)
    compileOnlyApi(libs.jspecifyAnnotations)

    compileOnly(libs.bundles.platformSupport)
}

indra {
    javaVersions {
        target(17)
    }
}
