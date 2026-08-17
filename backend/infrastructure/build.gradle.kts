// Los adaptadores: base de datos, clientes externos, almacenamiento y la
// configuracion tipada. Todo lo sucio y todo lo reemplazable.

plugins {
    id("sastra.spring-conventions")
}

dependencies {
    implementation(project(":application"))
    implementation(project(":domain"))

    // Spring Data JDBC, no JPA (ADR-0004).
    implementation(libs.spring.boot.starter.data.jdbc)

    // La configuracion se declara en clases @ConfigurationProperties validadas:
    // si falta una variable obligatoria, la aplicacion no arranca
    // (docs/operacion/configuracion.md).
    implementation(libs.spring.boot.starter.validation)

    // Integracion contra PostgreSQL real. H2 esta prohibido: se comporta
    // distinto y da falsa confianza (docs/arquitectura/pruebas.md).
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testRuntimeOnly(libs.postgresql)
}
