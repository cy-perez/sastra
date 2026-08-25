// Casos de uso y puertos de salida. Orquesta el dominio y define las
// interfaces que la infraestructura implementa.

plugins {
    id("sendik.spring-conventions")
}

dependencies {
    // `api` a proposito: presentation e infrastructure ven el dominio a traves
    // de los casos de uso, sin volver a declararlo por su cuenta.
    api(project(":domain"))

    // Lo unico que se le permite de todo Spring: @Transactional
    // (backend/CLAUDE.md). Cualquier otra cosa de Spring aqui es un error.
    implementation(libs.spring.tx)

    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
}
