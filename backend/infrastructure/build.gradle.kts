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

    // Argon2id para las contrasenas, nunca BCrypt (backend/CLAUDE.md). Solo el
    // modulo de criptografia: aqui no se configura ningun filtro HTTP.
    // BouncyCastle no es opcional, Argon2PasswordEncoder lo exige.
    implementation(libs.spring.security.crypto)
    implementation(libs.bouncycastle.provider)

    // RestClient para los dos clientes externos (ADR-0012 y ADR-0013). Solo
    // spring-web: aqui no se atiende ninguna peticion HTTP.
    implementation(libs.spring.web)

    // El convertidor JSON que RestClient usa para el cuerpo que va a Resend. Se
    // estaba usando sin declararlo: llegaba de rebote por el starter de webmvc de
    // `presentation`. Un modulo declara lo que usa, y de esto en concreto depende
    // que salga cada correo transaccional.
    implementation(libs.spring.boot.starter.json)

    // Emision del token de acceso, y el decodificador que consume la cadena de
    // seguridad. Va aqui porque necesita el secreto de firma, que es configuracion
    // y la configuracion vive en esta capa (ADR-0003).
    api(libs.spring.security.oauth2.jose)

    // Cloud Storage: los dos almacenes de archivos de ADR-0018. Solo se carga con
    // `sastra.storage.provider=gcs`; con `local` los beans no se crean y la
    // libreria no se toca.
    implementation(libs.google.cloud.storage)

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

// Los seis repositorios JDBC no se pueden probar desde este modulo: necesitan el
// esquema, el esquema lo define Flyway y las migraciones viven en `bootstrap`, que
// esta mas arriba en el grafo. Sus pruebas de integracion estan alli
// (SessionLifecycleTest, ProfileAndEmailChangeTest) y su cobertura se anota en el
// archivo de ejecucion de ese modulo.
//
// Por eso la regla de este modulo mide todo menos `persistence`: es lo que puede
// alcanzar con pruebas propias, y sobre eso si exige el 80%. Lo que queda fuera no
// queda sin medir, lo mide `verificarCoberturaAgregada` en la raiz, que une los
// cinco modulos. Antes de esa tarea, este modulo no tenia ninguna prueba y la
// verificacion se saltaba completa sin decir nada.
val clasesConPruebaPropia = the<SourceSetContainer>()
    .named("main")
    .get()
    .output
    .classesDirs
    .asFileTree
    .matching { exclude("co/sastra/identity/persistence/**") }

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    classDirectories.setFrom(clasesConPruebaPropia)
}
