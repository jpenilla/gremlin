import net.kyori.indra.IndraExtension

plugins {
    alias(libs.plugins.indra) apply false
}

subprojects {
    plugins.apply("net.kyori.indra")

    the<IndraExtension>().apply {
        publishSnapshotsTo("jmp", "https://repo.jpenilla.xyz/snapshots/")
    }
}
