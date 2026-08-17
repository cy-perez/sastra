---
description: Revision completa de los cambios pendientes antes de confirmarlos
allowed-tools: Read, Grep, Glob, Bash, Task
---

Revisa el trabajo pendiente en el arbol de trabajo.

1. Ejecuta `git status` y `git diff` para delimitar exactamente que cambio.
2. Lanza en paralelo los subagentes que correspondan al contenido del cambio:
   `arquitecto` siempre; `revisor-seguridad` si toca cuentas, sesiones, pagos,
   archivos subidos o datos de identidad; `revisor-accesibilidad` si hay
   interfaz; `revisor-pruebas` si hay logica de negocio.
3. Ejecuta la verificacion que corresponda: `gradlew.bat check` en el backend,
   `npm run verify` en el frontend. Reporta los fallos tal como salen, sin
   interpretarlos a la ligera.
4. Consolida todo en una sola lista ordenada por severidad, sin repetir lo que
   dos revisores hayan senalado por separado.
5. No corrijas nada todavia. Termina preguntando que se corrige y que se acepta.
