plugins {
    id("net.kyori.indra.publishing")
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("me.lucko:jar-relocator:1.7")
    compileOnlyApi("org.slf4j:slf4j-api:2.0.9")
    compileOnlyApi("org.jspecify:jspecify:0.3.0")

    compileOnly("io.papermc.paper:paper-api:1.20.2-R0.1-SNAPSHOT")
    compileOnly("net.fabricmc:fabric-loader:0.14.22")
    compileOnly("com.velocitypowered:velocity-api:3.2.0-SNAPSHOT")
}

indra {
    javaVersions {
        target(17)
    }
}
