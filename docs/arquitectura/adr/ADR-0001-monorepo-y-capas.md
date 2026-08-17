# ADR-0001 — Monorepo y arquitectura por capas estricta

**Fecha:** 2026-08-15 · **Estado:** aceptada

## Contexto

Un desarrollador solo, backend y frontend, con asistencia de IA como parte
central del flujo de trabajo. La aplicación crecerá hacia pagos, envíos y una
aplicación móvil.

## Decisión

Un solo repositorio con `backend/` y `frontend/`, y cuatro capas estrictas en
ambos: `domain`, `application`, `infrastructure`, `presentation`.

## Motivo

**Monorepo.** El contrato entre las dos puntas cambia constantemente en un
producto joven. En un solo repositorio, un cambio de API y su consumo entran en
el mismo commit, se revisan juntos y no existe la posibilidad de que las
versiones se desincronicen. Además, el agente puede leer las dos puntas a la vez:
con repositorios separados tendría que adivinar la mitad del contrato.

**Capas estrictas.** El costo es escribir mapeadores que a primera vista parecen
repetitivos. El beneficio es que la lógica de negocio queda aislada de decisiones
que sí van a cambiar: la pasarela, el proveedor de nube, el marco web. En un
producto con reglas de dinero, esa lógica es el activo que hay que proteger.

Hay un segundo motivo, propio de trabajar con IA: una estructura estricta y
predecible da al agente un lugar evidente para cada cosa. La libertad
arquitectónica produce, en este contexto, inconsistencia.

## Consecuencias

- Más archivos por funcionalidad, sobre todo al principio.
- Los despliegues de backend y frontend son independientes aunque el repositorio
  sea uno solo.
- El dominio es portable a la aplicación móvil sin arrastrar infraestructura.

## Cuándo revisar

Si entra un equipo separado para el frontend con su propio ciclo de entrega.
