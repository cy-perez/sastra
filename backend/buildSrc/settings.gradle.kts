rootProject.name = "buildSrc"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        // Los plugins de convencion leen las versiones del mismo catalogo que
        // el resto del backend. No hay un segundo sitio donde mantenerlas.
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
