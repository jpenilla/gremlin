plugins {
    id("base-conventions")
    id("net.kyori.indra.publishing")
}

repositories {
    mavenCentral()
}

indra {
    javaVersions {
        target(17)
    }
}
