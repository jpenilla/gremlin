plugins {
    id("parent-conventions")
    alias(libs.plugins.indra.publishing.sonatype)
}

indraSonatype {
    useAlternateSonatypeOSSHost("s01")
}
