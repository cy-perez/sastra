# ADR-0007 — TanStack Query pese a su estado experimental

**Fecha:** 2026-08-15 · **Estado:** aceptada, con condiciones

## Contexto

Hay que manejar datos remotos: caché, revalidación, reintentos, estados de carga
y error, invalidación tras mutaciones.

El adaptador de Angular se publica como `@tanstack/angular-query-experimental` y
sus autores advierten que puede romper compatibilidad **incluso en versiones de
parche**. Al mismo tiempo, Angular ya ofrece `resource` y `httpResource` propios.

## Decisión

Se usa TanStack Query, con tres condiciones que no son negociables.

## Motivo

Lo que aporta y Angular todavía no cubre igual: invalidación por claves,
deduplicación de peticiones simultáneas, datos obsoletos mientras revalida,
reintentos con espera creciente y consultas infinitas para el catálogo. Escribir
eso a mano es más riesgo que depender de una librería madura, aunque su capa de
Angular sea joven: el núcleo lleva años en producción en otros ecosistemas.

## Condiciones

1. **Versión fijada exacta** en `package.json`, sin `^` ni `~`. Subirla exige una
   ADR y ejecutar toda la suite.
2. **Nunca se usa desde un componente.** Se envuelve en servicios de la capa
   `application`. Los componentes reciben señales. Si hubiera que reemplazar la
   librería, el cambio queda confinado a esa capa.
3. **Se revisa en cada versión mayor de Angular** si `httpResource` ya cubre lo
   necesario. El día que lo haga, se migra y se elimina la dependencia.

## Consecuencias

- Una dependencia experimental en un lugar central, mitigada por el aislamiento.
- Menos código propio de sincronización y menos errores sutiles de caché.

## Cuándo revisar

Al estabilizarse el adaptador de Angular, o al primer cambio incompatible que
cueste más de una tarde.
