plugins {
    `kotlin-dsl`
}

dependencies {
    // Para poder aplicar Spotless dentro de un plugin de convencion hace falta
    // su artefacto aqui, no su id.
    implementation(libs.spotless.gradle.plugin)
}
