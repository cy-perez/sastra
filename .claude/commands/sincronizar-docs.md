---
description: Detecta desviaciones entre la documentacion y el codigo real
allowed-tools: Read, Grep, Glob, Bash
---

La documentacion solo sirve si es cierta. Compara y reporta, sin corregir nada
todavia.

Revisa:

- Endpoints implementados frente a `docs/arquitectura/contrato-api.md`.
- Tablas y columnas de las migraciones de Flyway frente a
  `docs/arquitectura/modelo-datos.md`.
- Variables leidas por la aplicacion frente a `docs/operacion/configuracion.md`
  y `.env.example`.
- Versiones reales en `libs.versions.toml` y `package.json` frente a la tabla de
  `CLAUDE.md`.
- Terminos de dominio usados en el codigo frente a `docs/producto/glosario.md`.
- Reglas de negocio implementadas frente a `docs/producto/reglas-negocio.md`,
  en especial cualquier numero: comision, plazos, limites.

Para cada desviacion indica cual de los dos esta desactualizado, el codigo o el
documento, y propon la correccion. Al final, pregunta que aplico.
