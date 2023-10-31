import net.kyori.indra.IndraExtension

plugins {
    alias(libs.plugins.indra) apply false
    alias(libs.plugins.indraSonatypePublishing)
}

indraSonatype {
    useAlternateSonatypeOSSHost("s01")
}

subprojects {
    plugins.apply("net.kyori.indra")

    the<IndraExtension>().apply {
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

        github("jpenilla", "gremlin")
    }
}
