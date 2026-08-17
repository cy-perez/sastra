# ADR-0002 — Gradle multi-módulo para hacer cumplir las capas

**Fecha:** 2026-08-15 · **Estado:** aceptada

## Contexto

Las capas se pueden respetar con paquetes y disciplina, o hacerse cumplir por el
sistema de construcción.

## Opciones

1. Un módulo con paquetes por capa. Simple, pero nada impide un import
   equivocado. Depende por completo de la disciplina de quien escribe, sea
   persona o agente.
2. Multi-módulo. Más configuración inicial; a cambio, la violación no compila.

## Decisión

Multi-módulo con Gradle y Kotlin DSL: `domain`, `application`, `infrastructure`,
`presentation`, `bootstrap`.

## Motivo

Una regla que el compilador hace cumplir vale más que una documentada. `domain`
no declara Spring como dependencia, así que **no puede** importarlo: el error es
imposible, no improbable. Esto importa especialmente cuando parte del código lo
escribe un agente que ha visto miles de ejemplos donde la entidad de dominio
lleva anotaciones de JPA.

El catálogo de versiones `libs.versions.toml` queda además como fuente única de
verdad para las versiones, que es exactamente lo que el agente debe consultar en
lugar de recordar.

## Consecuencias

- Configuración inicial mayor y compilación algo más lenta.
- Los límites son visibles en el árbol de archivos, no solo en la documentación.
- Extraer un contexto a un servicio aparte, si algún día hace falta, es mecánico.

## Cuándo revisar

Si el tiempo de compilación se vuelve un obstáculo real y medido.
