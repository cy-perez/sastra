// El borde HTTP: controladores, DTO de peticion y respuesta, y traduccion de
// errores a ProblemDetail. No decide nada de negocio.

plugins {
    id("sastra.spring-conventions")
}

dependencies {
    implementation(project(":application"))
    implementation(project(":domain"))

    // El starter especifico, no el monolitico spring-boot-starter-web
    // (backend/CLAUDE.md).
    implementation(libs.spring.boot.starter.webmvc)

    // Jakarta Validation en el DTO de entrada. La otra mitad de la validacion
    // la hace el dominio: las dos, no una.
    implementation(libs.spring.boot.starter.validation)

    // OpenAPI generado desde el codigo, servido en /swagger-ui.html
    // (docs/arquitectura/contrato-api.md). Apagado en el perfil prod.
    implementation(libs.springdoc.openapi.webmvc.ui)

    // Spring Security 7.1: el borde declara que rutas quedan abiertas y cuales
    // no. Nada queda accesible por omision (backend/CLAUDE.md).
    implementation(libs.spring.boot.starter.security)

    // Validacion del token de acceso en cada peticion. El decodificador lo aporta
    // infrastructure, que es quien tiene el secreto; aqui solo se declara que la
    // cadena lo use (ADR-0003).
    implementation(libs.spring.boot.starter.oauth2.resource.server)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.security.test)
}
