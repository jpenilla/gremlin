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

tasks {
    withType<Jar> {
        from(rootProject.file("LICENSE")) {
            rename { "META-INF/LICENSE_${rootProject.name}" }
        }
    }
    jar {
        manifest {
            attributes(
                "Automatic-Module-Name" to "xyz.jpenilla.gremlin.runtime"
            )
        }
    }
}
