plugins {
    id("base-conventions")
    id("net.kyori.indra.publishing")
    id("org.incendo.cloud-build-logic.publishing")
}

repositories {
    mavenCentral()
}

indra {
    javaVersions {
        target(17)
    }
}
