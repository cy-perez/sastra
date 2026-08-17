import java.math.BigDecimal
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

// Lo comun a los cinco modulos: version de Java, rigor del compilador, motor de
// pruebas, formato y cobertura. Nada de Spring vive aqui: `domain` tambien
// aplica este plugin y no puede ver el framework ni de lejos.

plugins {
    `java-library`
    jacoco
    id("com.diffplug.spotless")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

group = "co.sastra"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        // La version se lee del catalogo, nunca se escribe suelta aqui.
        languageVersion = JavaLanguageVersion.of(libs.findVersion("java").get().requiredVersion)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // Una advertencia es un error. CLAUDE.md prohibe suprimirlas en lugar de
    // corregirlas, asi que el build no deja pasar ninguna.
    // -parameters: Spring necesita los nombres de parametro para enlazar
    // configuracion y peticiones sin anotaciones redundantes.
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror", "-parameters"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = TestExceptionFormat.FULL
    }
}

dependencies {
    testImplementation(platform(libs.findLibrary("junit-bom").get()))
    testImplementation(libs.findLibrary("junit-jupiter").get())
    testImplementation(libs.findLibrary("assertj-core").get())
    testRuntimeOnly(libs.findLibrary("junit-platform-launcher").get())
}

spotless {
    java {
        target("src/**/*.java")
        palantirJavaFormat(libs.findVersion("palantir-java-format").get().requiredVersion)
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

jacoco {
    toolVersion = libs.findVersion("jacoco").get().requiredVersion
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    reports {
        xml.required = true
        html.required = true
    }
}

// Cobertura minima global del 80% (docs/arquitectura/pruebas.md). `domain` sube
// el liston al 90% con una regla adicional en su propio build.
tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                minimum = BigDecimal("0.80")
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.named("jacocoTestCoverageVerification"))
}
