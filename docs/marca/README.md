# Marca

Identidad visual de Sastra. `manual.md` es el documento completo; aquí está lo
que se necesita para programar.

## Contenido

```
manual.md      manual de marca completo
svg/           logos e isotipos en vector, fuente de verdad
dist/web/      favicons, iconos, manifiesto y fragmento para el head
dist/app/      iconos para iOS y Android
dist/social/   piezas compuestas para redes y previsualizaciones de enlaces
dist/raster/   versiones en PNG
```

## Qué usar en la web

- Cabecera y correos: `svg/logo-horizontal.svg`. Es el que más se usa.
- Móvil, favicon y avatar: `svg/isotipo.svg`.
- Fondos oscuros: `svg/logo-mono-negativo.svg` y `svg/isotipo-negativo.svg`.
- Todo lo de `dist/web/` va en la raíz del sitio, y el contenido de
  `head-snippet.html` se pega en el `head` del documento.

El logo se usa como imagen, no se redibuja en el código. Pesa 4KB y se ve nítido
en cualquier pantalla.

## Reglas que aplican al código

- Área de respeto: la mitad del ancho del isotipo por los cuatro lados. Nada
  entra ahí, ni texto ni bordes de caja.
- Tamaño mínimo: 24px de alto para el lockup horizontal, 16px para el isotipo.
  Por debajo de 24px se usa el isotipo solo. Por eso en móvil la cabecera lleva
  isotipo: con el buscador al lado, el lockup no cabe legible.
- No deformar, no rotar, no recolorear, no agregar sombras ni contornos, no
  cambiar la distancia entre símbolo y palabra, no escribir el nombre en
  minúsculas ni con otra tipografía.
- Sobre fotografía, capa de tinta al 70% detrás.

## Iconos de interfaz

Si se dibujan iconos propios, cinco reglas:

- Retícula de 24 x 24, área viva de 20 x 20.
- Trazo de 1.75px, el mismo en todos.
- Terminaciones rectas y uniones en ángulo, como los extremos del isotipo.
- `stroke="currentColor"`, para que hereden el color y funcionen en modo claro,
  oscuro y deshabilitado sin variantes.
- Radio de esquina de 4px, el mismo del sistema.

## Descriptor

"Compra y vende moda con respaldo". Estructura: verbo, categoría, promesa. Dice
respaldo y dice moda, y no promete precio.
