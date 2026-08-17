# ADR-0008 — Búsqueda con Typesense

**Fecha:** 2026-08-15 · **Estado:** aceptada, se implementa en Fase 3

## Contexto

El catálogo necesita búsqueda con tolerancia a errores de escritura, filtros
combinados por categoría, talla, condición, marca, color y precio, y orden por
relevancia. En moda, quien busca "camisa oxford azul" y no encuentra nada, se va.

## Opciones

1. **PostgreSQL con búsqueda de texto completo.** Cero infraestructura extra y
   suficiente para empezar. Se queda corto en tolerancia a errores, en facetas y
   en ajuste de relevancia; conforme crecen los filtros, las consultas se vuelven
   lentas y difíciles de mantener.
2. **Algolia.** Excelente y sin operación, con un plan gratuito acotado. El costo
   escala con el uso y no se puede autoalojar: es una dependencia permanente.
3. **Typesense.** Código abierto, autoalojable, facetas y tolerancia a errores de
   fábrica, con servicio administrado disponible si no se quiere operar.

## Decisión

Typesense, tras el puerto `SearchEngine` definido en `application`.

## Motivo

Cubre lo que el catálogo necesita y deja abierta la salida en las dos
direcciones: se puede empezar con su servicio administrado y mudarse a instancia
propia cuando el costo lo justifique, sin cambiar el código. Con Algolia esa
puerta no existe.

Como está tras un puerto, la primera implementación puede incluso ser
PostgreSQL mientras el catálogo sea pequeño, y el cambio a Typesense no toca
nada fuera de `infrastructure`.

## Consecuencias

- Hay que mantener sincronizado el índice con la base de datos, mediante eventos
  de dominio de publicación, edición, venta y despublicación.
- PostgreSQL sigue siendo la fuente de verdad; el índice es descartable y se
  puede reconstruir completo en cualquier momento.
- Un servicio más que operar y monitorear.
- Nada de esto se construye antes de Fase 3.

## Cuándo revisar

Si en Fase 3 el catálogo aún es pequeño, puede posponerse manteniendo la
implementación con PostgreSQL detrás del mismo puerto.
