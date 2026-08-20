// El proyecto raiz no tiene codigo: solo agrupa los cinco modulos. El grafo de
// dependencias esta en settings.gradle.kts y lo comun en buildSrc.
//
// `gradlew.bat check` desde aqui ejecuta compilacion, formato, pruebas y
// cobertura en todos los modulos, y ademas la cobertura agregada que se define
// abajo.

import java.math.BigDecimal
import java.util.concurrent.Callable
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    base
    jacoco
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

// Cobertura agregada de los cinco modulos juntos.
//
// **Por que no basta la del modulo.** JaCoCo mide por modulo: cada `test` escribe
// su propio archivo de ejecucion y cada informe solo mira las clases de su propio
// modulo. Eso dejaba un punto ciego. Los adaptadores de `infrastructure` —los seis
// repositorios JDBC— se ejercitan desde las pruebas de integracion de `bootstrap`,
// porque el esquema lo define Flyway y las migraciones viven alli, asi que no
// pueden probarse desde `infrastructure` sin duplicar el esquema. Su cobertura
// quedaba anotada en el archivo de `bootstrap` y el informe de `infrastructure` no
// la veia. Y mientras `infrastructure` no tuvo ninguna prueba propia, no tenia
// datos de ejecucion y su verificacion se **saltaba entera**: el minimo del 80%
// no se aplicaba a la capa que hashea las contrasenas y firma los tokens, y el
// build seguia verde.
//
// Un minimo que se salta solo es peor que no tenerlo, porque se lee como cumplido.
// Esta tarea une los datos de ejecucion de los cinco modulos y los mide contra
// todas las clases de produccion a la vez: una clase cubierta desde otro modulo
// cuenta, y ningun modulo puede quedar fuera de la medicion por no tener pruebas
// propias.
//
// Las reglas por modulo siguen en su sitio y son mas exigentes en `domain` (90%).
// Esta no las reemplaza: manda la que primero se incumpla.
//
// Todo se resuelve con `Callable` a proposito. El script de la raiz se evalua antes
// que los de los modulos, asi que preguntar aqui y ahora por sus `sourceSets` falla:
// todavia no tienen aplicado el plugin de Java. Envuelto en Callable, Gradle lo
// resuelve cuando la tarea se ejecuta, que es cuando la respuesta existe.
fun sourceSetPrincipalDe(modulo: Project) =
    modulo.the<SourceSetContainer>().named("main").get()

val clasesDeProduccion = files(Callable {
    subprojects.map { modulo ->
        sourceSetPrincipalDe(modulo).output.classesDirs.asFileTree.matching {
            // Misma exclusion que en bootstrap: la clase de arranque es
            // configuracion de framework sin logica propia, y
            // docs/arquitectura/pruebas.md dice expresamente que eso no se prueba.
            exclude("co/sastra/SastraApplication.class")
        }
    }
})

val fuentesDeProduccion = files(Callable {
    subprojects.map { modulo -> sourceSetPrincipalDe(modulo).allSource.srcDirs }
})

val datosDeEjecucion = files(Callable {
    subprojects.map { modulo ->
        modulo.fileTree(modulo.layout.buildDirectory.dir("jacoco")) { include("*.exec") }
    }
})

val pruebasDeTodosLosModulos = Callable { subprojects.map { it.tasks.named("test") } }

val informeAgregado = tasks.register<JacocoReport>("informeDeCoberturaAgregado") {
    group = "verification"
    description = "Informe de cobertura de los cinco modulos juntos."

    dependsOn(pruebasDeTodosLosModulos)

    classDirectories.setFrom(clasesDeProduccion)
    sourceDirectories.setFrom(fuentesDeProduccion)
    executionData.setFrom(datosDeEjecucion)

    reports {
        xml.required = true
        html.required = true
    }
}

val verificacionAgregada = tasks.register<JacocoCoverageVerification>("verificarCoberturaAgregada") {
    group = "verification"
    description = "Exige el 80% de cobertura sobre todo el codigo de produccion (docs/arquitectura/pruebas.md)."

    dependsOn(pruebasDeTodosLosModulos)
    mustRunAfter(informeAgregado)

    classDirectories.setFrom(clasesDeProduccion)
    sourceDirectories.setFrom(fuentesDeProduccion)
    executionData.setFrom(datosDeEjecucion)

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
    dependsOn(verificacionAgregada, informeAgregado)
}
