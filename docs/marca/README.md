# Marca

Identidad visual de Sendik. `manual.md` es el documento completo; aquí está lo
que se necesita para programar.

## Contenido

```
manual.md      manual de marca completo
svg/           logos e isotipos en vector, fuente de verdad (13 variantes)
tokens.css     los colores y medidas aprobados, como variables
tokens.json    los mismos, para herramientas de diseño
dist/web/      favicons, iconos, manifiesto y fragmento para el head
dist/app/      iconos para iOS y Android
dist/social/   piezas compuestas para redes y previsualizaciones de enlaces
dist/raster/   versiones en PNG y WebP del logo principal
revision-visual.html   hoja de revisión de la entrega
```

## Qué usar en la web

- Cabecera y correos: `svg/logo-horizontal.svg` (idéntico a `logo-principal.svg`,
  con el nombre que suele pedir quien programa). Es el que más se usa.
- Móvil, favicon y avatar: `svg/isotipo.svg`.
- Fondos oscuros: `svg/logo-mono-negativo.svg` y `svg/isotipo-negativo.svg`.
- Portada, pauta y firma de correo, mientras la marca no sea conocida:
  `svg/logo-con-eslogan.svg` y su versión negativa.
- Todo lo de `dist/web/` va en la raíz del sitio, y el contenido de
  `head-snippet.html` se pega en el `head` del documento.

El logo se usa como imagen, no se redibuja en el código. Son 3KB de vector y se
ve nítido en cualquier pantalla. El logotipo ya viene con el texto convertido a
curvas: no depende de tener Archivo instalada.

## Reglas que aplican al código

- **El logo es monocromo.** No hay versión bicolor ni degradado. Si alguien
  entrega un archivo de Sendik con degradado, está mal.
- Área de respeto: un cuarto del alto del isotipo por los cuatro lados
  (27 unidades sobre el lienzo de 120). Nada entra ahí, ni texto ni bordes de
  caja.
- Tamaño mínimo: **130px de ancho** para el lockup completo, **200px** si lleva
  eslogan, **16px** para el isotipo solo. Por debajo de 130px se usa el isotipo.
  Por eso en móvil la cabecera lleva isotipo: con el buscador al lado, el lockup
  no cabe legible.
- No deformar, no rotar, no recolorear fuera de la paleta, no agregar sombras ni
  contornos, no cambiar la distancia entre símbolo y palabra, no rellenar el
  corte del símbolo, no cambiar la redacción del eslogan.
- **Nunca el bronce `#B4884A` sobre fondo claro**: da 2.97:1. En claro va
  `#8A6428` sin excepción.

## El corte es la firma

El isotipo es una S de dos ganchos idénticos —el de abajo es el de arriba girado
180°— con un corte entre ellos: la misma pieza dos veces, porque en Sendik la
misma persona vende y compra, y el corte es el momento del traspaso.

Ese corte se repite fuera del logo como recurso gráfico: la línea interrumpida
que separa secciones (`.regla-corte`) y el borde superior de la insignia de
vendedor verificado. **Una sola vez por pieza.**

## La regla del acento

El bronce aparece **una vez por pantalla**, y siempre en el mismo sitio
conceptual: lo verificado, lo garantizado. El botón principal de compra va en
tinta, no en bronce. Si el bronce aparece en tres lugares deja de significar
algo.

## Iconos de interfaz

Si se dibujan iconos propios, cinco reglas:

- Retícula de 24 × 24, área viva de 20 × 20.
- Trazo de 2px, el mismo en todos.
- Terminaciones **rectas** (`butt` / `miter`), como los extremos del isotipo.
  Mezclar terminaciones es lo que hace que un set de iconos se vea amateur.
- `stroke="currentColor"`, para que hereden el color y funcionen en modo claro,
  oscuro y deshabilitado sin variantes.
- Radio de esquina de 4px, el mismo del sistema.

## Eslogan

**Compra y vende de forma ágil y segura.** No se escribe en mayúsculas, no se
pone en bronce y no se cambia la redacción. Ya está compuesto en
`svg/logo-con-eslogan.svg`.

## Sobre los generadores

Esta entrega no trae los scripts que produjeron el paquete: llegó como
entregable cerrado. Si hace falta un tamaño de icono o una pieza que no está, se
solicita a diseño en vez de retocar un activo suelto — un activo retocado a mano
rompe la coherencia del paquete.

Los tokens sí se pueden reconstruir: el sistema de interfaz que se sirve al sitio
sale de `docs/ui/generador/`, que extiende `tokens.json` de aquí. Ver
`docs/ui/ubicacion-de-activos.md`.
