import net.kyori.indra.IndraExtension
import net.kyori.indra.licenser.spotless.IndraSpotlessLicenserExtension

plugins {
    alias(libs.plugins.indra) apply false
    alias(libs.plugins.indraLicenser) apply false
    alias(libs.plugins.indraSonatypePublishing)
}

indraSonatype {
    useAlternateSonatypeOSSHost("s01")
}

subprojects {
    plugins.apply("net.kyori.indra")
    plugins.apply("net.kyori.indra.licenser.spotless")

    extensions.getByType<IndraExtension>().apply {
        configurePublications {
            pom {
                developers {
                    developer {
                        id.set("jmp")
                        timezone.set("America/Phoenix")
                    }
                }
            }
        }

        github("jpenilla", "gremlin") {
            ci(true)
        }

        apache2License()
    }

    extensions.getByType<IndraSpotlessLicenserExtension>().apply {
        licenseHeaderFile(rootProject.file("LICENSE_HEADER"))
    }
}
