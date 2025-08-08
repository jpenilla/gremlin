import org.incendo.cloudbuildlogic.jmp

plugins {
    id("net.kyori.indra")
    id("net.kyori.indra.licenser.spotless")
}

indra {
    configurePublications {
        pom {
            developers {
                jmp()
            }
        }
    }

    github("jpenilla", "gremlin") {
        ci(true)
    }

    apache2License()

    signWithKeyFromProperties("signingKey", "signingPassword")
}

indraSpotlessLicenser {
    licenseHeaderFile(rootProject.file("LICENSE_HEADER"))
}

tasks {
    withType<Jar> {
        from(rootProject.file("LICENSE")) {
            rename("LICENSE", "META-INF/LICENSE_${rootProject.name}")
        }
    }
}
