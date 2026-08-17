# Generadores de identidad

Estos dos scripts produjeron todo el paquete de marca. No se ejecutan en el
desarrollo diario: se guardan porque son la única forma de regenerar los activos
de forma consistente si algo cambia.

| Script | Qué genera |
|---|---|
| `build_marca.py` | Los SVG del logo, isotipo, favicon y variantes monocromas, más los PNG de `dist/` |
| `build_piezas.py` | Las piezas de redes: og-image, tarjeta de X, portadas, historia |

Toda la geometría sale de la misma construcción: arcos de radio 22 sobre lienzo
120, trazo 15 y un corte de 9 unidades a 135 grados, que es la puntada. Un solo
`<path>` por activo, sin máscaras ni `<text>`.

## Cuándo se ejecutan

- Si cambia un color de marca en `../tokens.json`.
- Si hace falta un tamaño de icono que no existe.
- Nunca para retocar un activo suelto: se corrige el generador y se regenera
  todo, o el paquete deja de ser coherente.

## Qué necesitan

Python con `fonttools` y `skia-pathops`, más el archivo de Archivo (la fuente)
en el sistema: el logotipo se construye convirtiendo el texto a curvas, no
enlazando la fuente. Por eso los SVG entregados se ven igual en cualquier
máquina sin tener la tipografía instalada.

Antes de ejecutarlos, revisar las rutas de salida: escriben sobre el paquete
existente.
