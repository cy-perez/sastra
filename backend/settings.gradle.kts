// El grafo de modulos es la primera defensa de la arquitectura: un modulo no
// puede importar lo que no declara. Ver ADR-0002 y backend/CLAUDE.md.
//
//   domain          sin dependencias de framework
//   application     depende de: domain
//   infrastructure  depende de: application, domain
//   presentation    depende de: application, domain
//   bootstrap       depende de todos. Contiene el main y el cableado.
//
// Cambiar este grafo exige una ADR.

plugins {
    // Descarga el JDK del toolchain (y el del demonio) cuando la maquina no lo
    // tiene instalado. Sin esto, quien clone el repo necesita el JDK 25 a mano.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "sastra-backend"

dependencyResolutionManagement {
    // Ningun modulo declara sus propios repositorios: se resuelven todos aqui.
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}

include(
    "domain",
    "application",
    "infrastructure",
    "presentation",
    "bootstrap",
)
