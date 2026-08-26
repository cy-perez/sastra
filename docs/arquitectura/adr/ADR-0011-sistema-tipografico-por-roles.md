# ADR-0011 — El tipo se aplica por rol, no por tamaño

Fecha: 2026-08-16 · Estado: aceptada

## Contexto

El kit de interfaz entregó `tipografia.css`: trece roles de texto, escala fluida
con `clamp()` para titulares, cuerpo fijo en 16px, numerales tabulares para
precios y familias de respaldo ajustadas. Deja resuelto un problema real de
accesibilidad y hay que decidir cómo se consume desde los componentes.

La alternativa natural para quien programa es escribir `font-size` donde haga
falta. Es también la forma habitual de que el sistema se degrade: en seis meses
hay quince tamaños de letra, ninguno igual a otro, y bajar de `h2` a `h3` se
vuelve la manera de hacer que algo se vea más pequeño.

## Decisión

`tipografia.css` es la única fuente de verdad del tipo. Ningún componente, ningún
estilo global y ninguna otra hoja definen `font-size`, `font-family` ni
`font-weight` numérico. El texto se aplica con la clase de rol correspondiente.

Si un diseño necesita algo que no existe, se agrega el rol en `tipografia.css` y
se documenta. No se resuelve en el componente.

Un hook rechaza la escritura de tipografía fuera del sistema. Las cuatro hojas de
`frontend/src/styles/` están exentas, porque son justamente donde el sistema se
define.

## Motivo

Separa dos decisiones que no tienen por qué coincidir: el **nivel semántico** del
encabezado, que lo fija la estructura del documento y del que depende la
navegación de un lector de pantalla, y el **tamaño visual**, que lo fija el
diseño. Con roles, un `h2` puede verse pequeño sin convertirse en `h3`.

La escala fluida y el cuerpo fijo en 16px resuelven dos fallos concretos ya
verificados por el kit: un titular de 62px ocupando media pantalla en celular, y
el zoom automático que hace el navegador móvil al enfocar un campo cuando el
texto baja de 16px.

## Consecuencias

- Un componente nuevo empieza eligiendo rol, no tamaño.
- Falta un rol es una señal legítima para ampliar el sistema, no una excusa para
  saltárselo.
- Los tres pesos disponibles son 400, 500 y 600. Un diseño que pida otro se
  ajusta al sistema.
- Queda pendiente medir los `size-adjust` del bloque de respaldo contra los
  archivos reales, o eliminarlo. El aviso vive junto al bloque que describe, en
  `frontend/src/styles/tipografia.css`.

## Cuándo revisar

Si aparece un cuarto peso justificado, si el catálogo exige una densidad de texto
que la escala actual no cubre, o si al medir los respaldos resulta que el ajuste
no aporta.

## Actualización

**25 de agosto de 2026, con el cambio de identidad a Sendik (ADR-0022).** La
decisión de esta ADR —el tipo se aplica por rol y no por tamaño— no cambia; sí
cambian las familias y un peso.

- Salen **Instrument Sans** e **IBM Plex Mono**, entran **Inter** y **Archivo**,
  ambas variables. La familia monoespaciada desaparece: existía solo para que los
  precios alinearan en columna, y el manual de Sendik resuelve ese caso con
  `font-variant-numeric: tabular-nums` sobre la familia de texto. Dos familias
  cumplen el mismo trabajo con una descarga menos.
- El peso fuerte pasa de **700 a 600**: es el único que la marca define para
  Archivo, y el máximo que usa Inter. Siguen siendo tres.
- Los roles no cambian de nombre. Un componente escrito contra `.tipo-h2` o
  `.precio` sigue siendo correcto sin tocarlo, que es precisamente lo que esta
  ADR buscaba al separar el rol del tamaño.
