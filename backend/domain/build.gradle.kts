import java.math.BigDecimal
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

// El corazon. Sin Spring, sin JPA, sin Jackson, sin Lombok: solo lenguaje
// estandar y las anotaciones de nulidad de JSpecify.
//
// Que esta lista no crezca es la razon de ser del multi-modulo (ADR-0002):
// aqui no se puede importar lo que no esta declarado. Agregar una dependencia
// a este modulo exige una ADR.

plugins {
    id("sastra.java-conventions")
}

dependencies {
    api(libs.jspecify)
}

// El dominio es la unica capa de reglas de negocio puras y se prueba sin
// infraestructura: 90% minimo (docs/arquitectura/pruebas.md). Se suma a la
// regla del 80% que fija la convencion; manda la mas exigente.
tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                minimum = BigDecimal("0.90")
            }
        }
    }
}
