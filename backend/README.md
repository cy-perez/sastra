# Backend

API de Sendik. Java 25, Spring Boot 4.1.0, Gradle 9 multi-modulo, PostgreSQL 17,
Spring Data JDBC y Flyway.

Las convenciones de codigo estan en `CLAUDE.md`. Este archivo es solo para
ponerlo en marcha.

## Modulos

```
domain          reglas de negocio puras, sin framework
application     casos de uso y puertos
infrastructure  base de datos, clientes externos, configuracion tipada
presentation    controladores REST
bootstrap       main, perfiles, migraciones y pruebas de cableado
buildSrc        plugins de convencion: toolchain, compilador, formato, cobertura
```

Las dependencias van siempre hacia adentro. El grafo de Gradle lo impide y
`ArchitectureTest`, en `bootstrap`, lo comprueba con doce reglas.

`domain` aplica solo `sendik.java-conventions`: no recibe ni el BOM de Spring.
Su unica dependencia es JSpecify. Los otros cuatro modulos aplican
`sendik.spring-conventions`, que agrega la plataforma de versiones.

## Requisitos

Docker y Git. **El JDK no se instala a mano.** El criterio de JVM del demonio
esta fijado en `gradle/gradle-daemon-jvm.properties` y Gradle descarga Temurin
25 en la primera construccion. Tarda unos minutos esa vez y ninguna mas.

## Arranque local

```
docker compose up -d      # desde la raiz del repositorio
gradlew.bat bootRun
```

`bootRun` arranca con el perfil `local` y se ejecuta desde `backend/`, de modo
que el `optional:file:../.env` de `application.yaml` apunta al `.env` de la raiz,
el mismo que lee Docker Compose. Si las credenciales de los dos no coinciden, el
arranque falla en Flyway con `password authentication failed`.

Sin `.env` tambien arranca: el perfil `local` trae valores por omision para
localhost. Los perfiles `dev` y `prod` no traen ninguno a proposito, para que una
variable olvidada rompa el despliegue y no la produccion.

| Recurso | Donde |
|---|---|
| API | `http://localhost:8080` |
| Estado | `/actuator/health` |
| Migraciones aplicadas | `/actuator/flyway` |
| Documentacion de la API | `/swagger-ui.html` |
| OpenAPI en JSON | `/v3/api-docs` |

Los dos ultimos estan **apagados en `prod`**: describen la superficie completa
del sistema y no le sirven a nadie de fuera.

## Comandos

| Accion | Comando |
|---|---|
| Compilar | `gradlew.bat build` |
| Pruebas | `gradlew.bat test` |
| Verificacion completa | `gradlew.bat check` |
| Formato | `gradlew.bat spotlessApply` |
| Arrancar | `gradlew.bat bootRun` |
| Empaquetar | `gradlew.bat bootJar` |
| Estado de las migraciones | `curl localhost:8080/actuator/flyway` |

`check` encadena compilacion, formato, pruebas y cobertura. Es lo que debe pasar
antes de confirmar cualquier cambio.

## Versiones

`gradle/libs.versions.toml` es la unica fuente de verdad y se lee, no se recuerda
(ADR-0002). Lo que gestiona el BOM de Spring Boot va sin version; lo que vive
fuera del mundo Spring la lleva, alineada a proposito con la que gestiona el BOM.

El compilador corre con `-Xlint:all -Werror`: una advertencia rompe la
construccion. No se suprime, se corrige.

## Configuracion

Los valores externos se declaran en clases `@ConfigurationProperties` validadas,
en `infrastructure/co/sendik/shared/config/`:

| Clase | Prefijo | Que cubre |
|---|---|---|
| `AppProperties` | `sendik.app` | URL publicas, correo de soporte, origenes CORS |
| `CompanyProperties` | `sendik.company` | Razon social, NIT, direccion |
| `CommissionProperties` | `sendik.commission` | Tasa de comision, entre 0 y 1 |
| `FeatureFlags` | `sendik.features` | Las cinco banderas, todas apagadas todavia |
| `StorageProperties` | `sendik.storage` | Proveedor de archivos, los dos cubos y los limites de imagen |

Las de JWT y de correo viven junto a lo que configuran, en
`infrastructure/co/sendik/identity/config/`: `SessionProperties`, `MailProperties`,
`PasswordSecurityProperties` y `LegalDocumentProperties`.

Si falta una variable obligatoria, la aplicacion no arranca. La lista completa
esta en `../docs/operacion/configuracion.md` y el ejemplo en `../.env.example`.

## Base de datos

El esquema lo gobierna Flyway. Las migraciones estan en
`bootstrap/src/main/resources/db/migration` con nombre
`V<n>__descripcion_en_ingles.sql`.

Hoy existe `V1__baseline.sql`, que solo activa la extension `citext`. Las tablas
de usuarios llegan en la siguiente migracion, con HU-001.

Una migracion aplicada no se edita nunca: se crea la siguiente. La generacion
automatica de esquema esta desactivada y debe seguir asi.

## Pruebas

Unitarias sin Spring para `domain` y `application`. Integracion con
Testcontainers y PostgreSQL 17 real, nunca H2. `@SpringBootTest` unicamente en
`bootstrap` y solo para caminos completos.

Lo que hay hoy, todo en `bootstrap`:

| Prueba | Que verifica |
|---|---|
| `ArchitectureTest` | Doce reglas de capas, nombres y API vigentes |
| `ApplicationContextTest` | El contexto levanta y la configuracion obligatoria esta |
| `FlywayMigrationsTest` | Las migraciones corren sobre PostgreSQL real |

La cobertura minima la exige `check`: 90% en `domain`, 80% en el resto.
`SendikApplication` queda fuera de la medicion por ser configuracion de framework
sin logica propia.
