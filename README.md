# Sastra

Marketplace colombiano donde cualquiera compra y vende moda, nueva y usada, con
la plataforma como respaldo de la transacción. La comisión es del 5% sobre el
valor del producto, a cargo del vendedor.

## Estado

Fase 1: cimientos del proyecto, registro y autenticación de usuarios, y páginas
informativas del sitio. El detalle por fases está en `docs/producto/alcance.md`.

## Requisitos locales

| Herramienta | Versión | Verificación |
|---|---|---|
| Node | 22 LTS | `node -v` |
| npm | 10 o superior | `npm -v` |
| Docker Desktop | actual | `docker ps` |
| Git | 2.40 o superior | `git --version` |

Ni Gradle ni el JDK se instalan. Gradle llega por el wrapper (`gradlew.bat` en
Windows) y este trae fijado el criterio de JVM del demonio, así que descarga
Temurin 25 por su cuenta en la primera construcción del backend.

## Arranque

```
docker compose up -d          # PostgreSQL local. Typesense entra en Fase 3
cd backend && gradlew.bat bootRun
cd frontend && npm install && npm start
```

Backend en `http://localhost:8080`, frontend en `http://localhost:4200`,
documentación de la API en `http://localhost:8080/swagger-ui.html`.

Antes del primer arranque, copiar `.env.example` a `.env` y completar los
valores. Ninguno de los valores reales se versiona.

## Estructura

```
backend/     Java 25, Spring Boot 4.1, Gradle multi-módulo, PostgreSQL 17
frontend/    Angular 21 con SSR, Transloco, Vitest
docs/        producto, arquitectura, ui, marca, operación, trabajo con IA
.claude/     configuración de Claude Code: comandos, subagentes y hooks
```

## Documentación

| Necesito saber | Archivo |
|---|---|
| Qué es el producto y qué no es | `docs/producto/vision.md` |
| Qué entra en cada fase | `docs/producto/alcance.md` |
| Cómo se llaman las cosas | `docs/producto/glosario.md` |
| Reglas de negocio vigentes | `docs/producto/reglas-negocio.md` |
| Capas, dependencias y estructura de paquetes | `docs/arquitectura/vision-tecnica.md` |
| Decisiones técnicas y su motivo | `docs/arquitectura/adr/` |
| Modelo de datos | `docs/arquitectura/modelo-datos.md` |
| Convenciones de la API | `docs/arquitectura/contrato-api.md` |
| Estrategia de pruebas | `docs/arquitectura/pruebas.md` |
| Colores, tipografía y componentes | `docs/ui/README.md` |
| Dónde va cada activo de marca | `docs/ui/ubicacion-de-activos.md` |
| Guía visual navegable | `docs/ui/index.html` |
| Regenerar el sistema visual | `docs/ui/generador/README.md` |
| Uso del logo y los assets | `docs/marca/manual.md` |
| Entornos, despliegue y costos | `docs/operacion/entornos.md` |
| Variables de configuración | `docs/operacion/configuracion.md` |
| Tratamiento de datos personales | `docs/operacion/datos-personales.md` |
| Cómo trabajar con el agente | `docs/ia/flujo-de-trabajo.md` |

## Comandos frecuentes

| Acción | Comando |
|---|---|
| Pruebas del backend | `cd backend && gradlew.bat test` |
| Pruebas del frontend | `cd frontend && npm test` |
| Pruebas de extremo a extremo | `cd frontend && npm run e2e` |
| Extremo a extremo con backend real | `cd frontend && npm run e2e:completo` |
| Cobertura de los cinco módulos juntos | `cd backend && gradlew.bat verificarCoberturaAgregada` |
| Verificación completa | `gradlew.bat check` y `npm run verify` |
| Estado de las migraciones | `curl localhost:8080/actuator/flyway` |

`npm run e2e:completo` arranca el backend de verdad, así que antes hace falta
PostgreSQL levantado (`docker compose up -d postgres`) y el artefacto empaquetado
(`cd backend && gradlew.bat :bootstrap:bootJar`). Si falta alguna de las dos, el
propio comando lo dice y explica cómo.

## Integración continua

`.github/workflows/verificacion.yml` se ejecuta en cada pull request y en cada
integración a `main`, con tres trabajos en paralelo:

| Trabajo | Qué hace |
|---|---|
| Backend | `gradlew check`: compila, Spotless, las reglas de ArchUnit, las pruebas con Testcontainers y el mínimo de cobertura, medido sobre los cinco módulos juntos |
| Frontend | `npm ci`, linter y formato, Vitest con cobertura, compilación y las pruebas de extremo a extremo con Playwright |
| Extremo a extremo completo | Levanta PostgreSQL, el backend empaquetado y el servidor de renderizado, y recorre los caminos de cuentas por la interfaz |

Si algo falla, los informes quedan como artefactos de la ejecución durante siete
días. Es lo mismo que corre en local: `gradlew.bat check` y `npm run verify`.

`.github/workflows/despliegue.yml` publica en `dev` con cada integración a `main`
y en `prod` con una etiqueta de versión y aprobación manual. Llama a la
verificación en lugar de repetir sus pasos, así que nada se publica sin pasarla
entera.

**Todavía no se ha desplegado nada, y es una decisión.** El sitio se publica
cuando el proyecto esté lo más completo posible; hasta entonces se prueba en local
integrado contra los servicios de GCP en capa gratuita. El hospedaje del sitio se
contrata con el dominio y su proveedor está por definir (ADR-0019). El motivo está
en `docs/operacion/entornos.md` y el procedimiento, listo para ese día, en
`docs/operacion/despliegue.md`.

Las dependencias las revisa Dependabot cada semana, agrupadas por ecosistema.
Las subidas de versión mayor no se proponen automáticamente: exigen una ADR.

## Licencia y titularidad

Proyecto privado. Sastra, NIT 1054994043-1, Medellín, Colombia.
