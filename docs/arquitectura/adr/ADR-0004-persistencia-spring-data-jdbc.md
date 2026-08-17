# ADR-0004 — Spring Data JDBC en lugar de JPA

**Fecha:** 2026-08-15 · **Estado:** aceptada

## Contexto

Con capas estrictas hay que elegir cómo persistir sin que la tecnología se filtre
al dominio.

## Opciones

1. **JPA con Hibernate.** Es lo más conocido y lo que más ejemplos tiene. A
   cambio trae carga perezosa, contexto de persistencia, entidades mutables con
   ciclo de vida propio y comportamiento difícil de predecir. En la práctica, la
   entidad de JPA termina siendo la entidad de dominio, y la capa interna queda
   atada a Hibernate. Es exactamente lo que esta arquitectura quiere evitar.
2. **Spring Data JDBC.** Modelo simple: se carga el agregado completo, se guarda
   el agregado completo. Sin sesión, sin proxies, sin sorpresas.
3. **jOOQ.** Excelente para SQL con tipos verificados, pero agrega generación de
   código y una curva propia que no se justifica hoy.

## Decisión

Spring Data JDBC para escritura y agregados, con `JdbcClient` y SQL explícito
para consultas de lectura complejas.

## Motivo

El modelo de Sastra encaja con agregados pequeños y bien delimitados: un usuario,
una publicación con sus ocho imágenes, un pedido con sus ítems. Nada de eso
necesita un grafo de objetos con carga perezosa.

Lo que se gana es previsibilidad: la consulta que se escribe es la que se
ejecuta. No aparecen consultas sorpresa ni el problema clásico de N+1 en
producción. Para alguien que desarrolla solo, eso vale más que cualquier
comodidad de mapeo.

Y hay un motivo de arquitectura: como las entidades de tabla viven en
`infrastructure` y no pueden llevar al dominio de la mano, el mapeo manual deja
de ser un capricho y pasa a ser el mecanismo que mantiene limpio el centro.

## Consecuencias

- Hay que escribir mapeadores entre el dominio y las tablas.
- Las consultas de varios agregados se resuelven con SQL o con vistas de lectura,
  no con relaciones automáticas.
- Migrar a JPA después sería costoso, aunque el dominio quedaría intacto, que es
  justamente el punto.
- Menos ejemplos disponibles: el agente tenderá a proponer JPA. `backend/CLAUDE.md`
  lo prohíbe explícitamente.

## Cuándo revisar

Si aparecen agregados con relaciones profundas donde el mapeo manual supere en
esfuerzo a lo que Hibernate resolvería.
