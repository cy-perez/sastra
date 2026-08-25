# Imagen del backend para Cloud Run (docs/operacion/entornos.md).
#
# Dos etapas. La primera construye con Gradle; la segunda solo lleva el JRE y la
# aplicacion, sin Gradle, sin el codigo fuente y sin el cache de dependencias.
#
# El jar se descomprime en capas en lugar de copiarse entero. Un jar ejecutable de
# Spring Boot pesa unos 50 MB, de los que el codigo propio es una fraccion minima:
# copiado de una pieza, cada despliegue reescribe una capa de 50 MB y Cloud Run la
# descarga completa. Separado, las dependencias quedan en una capa que solo cambia
# cuando cambian las versiones, y un despliegue normal mueve unos pocos cientos de
# kilobytes.

# ---------- Construccion ----------
FROM eclipse-temurin:25-jdk-noble AS construccion

WORKDIR /origen

# Primero solo lo que define las dependencias. Mientras esto no cambie, Docker
# reutiliza la descarga entera aunque el codigo cambie en cada commit.
COPY gradle/ gradle/
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY buildSrc/ buildSrc/

RUN chmod +x gradlew && ./gradlew --no-daemon dependencies --quiet || true

COPY domain/ domain/
COPY application/ application/
COPY infrastructure/ infrastructure/
COPY presentation/ presentation/
COPY bootstrap/ bootstrap/

# `bootJar` empaqueta y no arrastra `test`: aqui no se excluye nada, sencillamente
# esta tarea no las incluye. Las pruebas son un paso propio de la canalizacion y
# ya corrieron antes de llegar a construir la imagen; repetirlas dentro las
# pagaria dos veces sin que nadie viera el resultado.
RUN ./gradlew --no-daemon :bootstrap:bootJar --quiet

# Las capas salen directamente en el destino: `dependencies/`, `spring-boot-loader/`,
# `snapshot-dependencies/` y `application/`. Sin ninguna carpeta con el nombre del
# modulo por delante.
RUN mkdir -p /extraido \
    && java -Djarmode=tools -jar /origen/bootstrap/build/libs/bootstrap-*.jar \
       extract --layers --launcher --destination /extraido

# ---------- Ejecucion ----------
FROM eclipse-temurin:25-jre-noble

# Sin privilegios. Cloud Run no lo exige, y una aplicacion que no necesita ser
# root no debe serlo: si alguien consigue ejecutar algo dentro, la diferencia
# importa.
RUN groupadd --system sendik && useradd --system --gid sendik --create-home sendik

WORKDIR /aplicacion

# En orden de menos a mas volatil, que es lo que hace util el cache de capas: las
# dependencias solo cambian cuando cambia una version, y el codigo propio en cada
# commit.
COPY --from=construccion --chown=sendik:sendik /extraido/dependencies/ ./
COPY --from=construccion --chown=sendik:sendik /extraido/spring-boot-loader/ ./
COPY --from=construccion --chown=sendik:sendik /extraido/snapshot-dependencies/ ./
COPY --from=construccion --chown=sendik:sendik /extraido/application/ ./

USER sendik

# Documenta el puerto por omision. El de verdad lo decide Cloud Run con PORT y lo
# lee el arranque de abajo.
EXPOSE 8080

# El perfil no tiene valor por omision a proposito: en la nube
# SPRING_PROFILES_ACTIVE es obligatorio y olvidarlo debe romper el arranque, no
# arrancar en local por accidente (misma decision que en bootstrap/build.gradle.kts).
#
# MaxRAMPercentage porque el contenedor tiene un limite de memoria y la JVM, sin
# esto, calcula el monticulo sobre la memoria de la maquina: en un limite de 512 MB
# reservaria mas de lo que tiene y el planificador la mataria.
#
# Se arranca con el lanzador y no con `-jar`: la aplicacion esta desplegada en capas
# y aqui ya no hay ningun jar que ejecutar.
ENTRYPOINT ["sh", "-c", "exec java -XX:MaxRAMPercentage=75 org.springframework.boot.loader.launch.JarLauncher --server.port=${PORT:-8080}"]
