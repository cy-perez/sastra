---
description: Registra una decision tecnica como ADR
argument-hint: [decision a documentar]
allowed-tools: Read, Glob, Write
---

Documenta esta decision: $ARGUMENTS

1. Lee `docs/arquitectura/adr/README.md` y `PLANTILLA.md`, y revisa las ADR
   existentes: si alguna queda sustituida, hay que marcarla.
2. Antes de escribir, confirma conmigo la decision y, sobre todo, las opciones
   descartadas y su motivo. Una ADR sin alternativas descartadas no sirve de
   nada dentro de seis meses.
3. Crea `docs/arquitectura/adr/ADR-XXXX-titulo-en-espanol.md` con el siguiente
   numero libre, incluyendo la seccion de cuando revisarla.
4. Actualiza el indice del README de ADR y, si la decision cambia una regla
   vigente, senala que archivo de `CLAUDE.md` o de `docs/` queda desactualizado.
