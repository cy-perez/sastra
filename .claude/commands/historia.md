---
description: Redacta una historia de usuario nueva a partir de una idea, siguiendo la plantilla del proyecto
argument-hint: [descripcion breve de la funcionalidad]
allowed-tools: Read, Glob, Grep, Write
---

Vas a redactar una historia de usuario para: $ARGUMENTS

1. Lee `docs/producto/historias/PLANTILLA.md`, `docs/producto/reglas-negocio.md`,
   `docs/producto/glosario.md` y `docs/producto/alcance.md`.
2. Comprueba a que fase pertenece. Si es de una fase posterior a la vigente,
   dilo antes de continuar y pregunta si aun asi se documenta.
3. Hazme las preguntas que falten para escribir criterios verificables. No
   inventes reglas de negocio: lo que no este documentado, se pregunta.
4. Escribe el archivo como `docs/producto/historias/HU-XXX-titulo.md` con el
   siguiente numero libre. Criterios en formato dado / cuando / entonces, cada
   uno comprobable con una prueba.
5. Termina listando que reglas de negocio, campos del modelo de datos o entradas
   del glosario habria que agregar, sin agregarlas todavia.

No escribas codigo en este comando.
