# Manual de marca — Sendik

## Idea

Sendik es el marketplace colombiano donde cualquier persona compra y vende moda
—calzado, ropa, bolsos— nueva y usada, y tecnología solo nueva. No compite por
catálogo: compite por **confianza**. Vendedor verificado y pago integrado
(Wompi: PSE, Nequi, tarjetas, Bancolombia a la mano; y Addi) son el producto
real, y la identidad tiene que decir eso antes de que nadie lea una línea.

La marca es seria y premium, pero no corporativa: si pareciera un banco, la
persona que quiere vender unos tenis de su clóset sentiría que el sitio no es
para ella. La seriedad se consigue por disciplina geométrica y por lo que se
deja fuera, no por solemnidad.

## Eslogan

**Compra y vende de forma ágil y segura.**

Va debajo del logotipo, en Inter regular, gris `#6A6E80` sobre fondo claro o
blanco sobre fondo oscuro, ajustado al ancho exacto de la palabra SENDIK. No se
escribe en mayúsculas, no se pone en bronce y no se cambia la redacción. Está
listo en `logo-con-eslogan.svg` y su versión negativa.

## El símbolo y por qué es este

El isotipo es una **S construida con dos ganchos idénticos**: el de abajo es
exactamente el de arriba girado 180°. Entre los dos hay un corte.

Esa es la idea entera del negocio en una forma: son **la misma pieza dos veces**,
porque en Sendik la misma persona es vendedora y compradora. El corte es el
momento del traspaso — el punto donde uno entrega y el otro recibe, que es
justo donde la plataforma se para a verificar y a garantizar el pago.

**El corte es la firma de la marca.** Se repite fuera del logo como recurso
gráfico: la línea interrumpida que separa secciones (`.regla-corte` en
`tokens.css`), el borde superior del sello de vendedor verificado, la manera de
cortar una imagen. Regla: **una sola vez por pieza**.

### Qué se descartó y por qué

- **Bolsa, carrito, etiqueta, tienda y escudo**: es el promedio de lo que hay en
  el rubro y no dice nada de lo que hace distinta a Sendik.
- **Versión bicolor del isotipo** (un gancho bronce, uno tinta): se probó y se
  descartó porque los dos ganchos se despegan visualmente y la S deja de leerse
  como letra. El logo es monocromo.
- **Corte aplicado también a la S de la palabra**: se probó y se descartó porque
  exige máscaras SVG, que se rompen al exportar a PNG y no las soporta el formato
  de íconos de Android. La palabra queda limpia; el corte vive en el símbolo.
- **Versión "metalizada" con degradado dorado**: no es la marca. Un logo que
  necesita degradado para verse bien falla en el sello de caucho, en la factura,
  en el bordado y en la serigrafía a una tinta. Si alguien te entrega un archivo
  de Sendik con degradado, está mal: los archivos correctos usan un solo color
  plano y así están verificados en este paquete.

## Logo: variantes y cuándo usar cada una

| Archivo | Cuándo |
|---|---|
| `logo-principal.svg` | Uso por defecto. Cabecera del sitio, facturas, documentos |
| `logo-horizontal.svg` | Idéntico al principal — mismo archivo con el nombre que suele pedir un desarrollador para la barra de navegación |
| `logo-con-eslogan.svg` | Cuando la marca aún no es conocida: portada, pauta, primera diapositiva, firma de correo |
| `logo-con-eslogan-negativo.svg` | Lo mismo sobre fondo oscuro o sobre foto |
| `logo-vertical.svg` | Espacios cuadrados: pendones, pauta cuadrada, sellos |
| `logo-mono-positivo.svg` | Un solo color oscuro: sello de caucho, grabado láser, serigrafía a una tinta |
| `logo-mono-negativo.svg` | Un solo color claro: fondos oscuros, fotos, modo oscuro |
| `isotipo.svg` | Solo el símbolo: avatar de redes, base del ícono de app, marca de agua |
| `isotipo-negativo.svg` | El símbolo sobre fondo oscuro |
| `icono-app.svg` | Ícono de aplicación (símbolo blanco sobre teja tinta, a sangre) |
| `favicon.svg` | Favicon vectorial con esquinas redondeadas |
| `banner.svg` | Tarjeta social 1200×630, base de `og:image` |

Como el logo es monocromo por diseño, `logo-principal` y `logo-mono-positivo`
son gráficamente iguales. No es un descuido: se entregan con los dos nombres
porque las imprentas y los desarrolladores piden cosas distintas.

## Área de respeto y tamaño mínimo

- **Área de respeto**: un cuarto del alto del isotipo por los cuatro costados
  (27 unidades sobre el lienzo de 120). Nada entra ahí: ni texto, ni otro logo,
  ni el borde de la pantalla o del papel.
- **Tamaño mínimo del logo completo**: 130 px de ancho en pantalla, 30 mm
  impreso. Probado: por debajo de eso el corte del símbolo se cierra y la
  palabra empieza a empastarse.
- **Con eslogan**: mínimo 200 px de ancho. Por debajo, el eslogan no se lee y hay
  que usar `logo-principal.svg`.
- **Tamaño mínimo del isotipo solo**: 16 px. Para favicon usar
  `dist/web/favicon.ico`, que ya va simplificado y con el trazo engrosado.

## Usos prohibidos

No deformar ni estirar · no rotar · no recolorear fuera de la paleta · **no añadir
degradados, sombras ni contornos** · no poner el logo tinta sobre fondo oscuro
(usar el negativo) · no reencuadrar ni cambiar la separación entre símbolo y
palabra · no cambiar la redacción del eslogan · no rellenar el corte del símbolo ·
no meter el logo dentro de una caja o círculo que no esté en este manual · no usar
el bronce `#B4884A` sobre fondo claro.

## Color

| Rol | HEX | RGB | Dónde puede ir |
|---|---|---|---|
| Primario (tinta) | `#14162B` | 20, 22, 43 | Todo. Sobre blanco 17.81:1, sobre fondo 16.50:1 (AAA) |
| Primario oscuro | `#0A0B18` | 10, 11, 24 | Hover/pressed; superficie base en modo oscuro |
| Acento bronce | `#B4884A` | 180, 136, 74 | **Solo sobre fondo oscuro** (5.56:1 sobre tinta) |
| Acento bronce oscuro | `#8A6428` | 138, 100, 40 | **Solo sobre fondo claro** (4.95:1 sobre `#F6F6F8`) |
| Fondo | `#F6F6F8` | 246, 246, 248 | Fondo de página |
| Superficie | `#FFFFFF` | 255, 255, 255 | Tarjetas de producto |
| Texto suave | `#6A6E80` | 106, 110, 128 | Talla, estado del artículo, eslogan (5.05:1 sobre blanco) |
| Borde | `#E3E4EA` | 227, 228, 234 | Divisores de 1 px |
| Éxito | `#1E7A5A` | 30, 122, 90 | Pago aprobado, entrega confirmada (5.27:1) |
| Error | `#B3261E` | 179, 38, 30 | Pago rechazado (6.54:1) |

**La regla del acento**: el bronce aparece **una sola vez por pantalla**, y
siempre en el mismo sitio conceptual — lo verificado, lo garantizado. Si aparece
en tres lugares deja de significar algo. El botón principal de compra va en
tinta, no en bronce.

**Ojo con el bronce sobre fondo claro**: `#B4884A` da 2.97:1 sobre el fondo de
página, por debajo del mínimo accesible. En claro se usa `#8A6428` sin
excepción. Están separados en los tokens justamente para que no se confundan.

## Tipografía

| Uso | Familia | Licencia | Dónde bajarla |
|---|---|---|---|
| Display: logotipo, titulares, precios grandes | **Archivo** (peso 600) | SIL OFL 1.1 | fonts.google.com/specimen/Archivo |
| Texto: cuerpo, interfaz, formularios, eslogan | **Inter** (400 / 500 / 600) | SIL OFL 1.1 | fonts.google.com/specimen/Inter |

Ambas son gratuitas y de uso comercial libre, incluido el bordado y la imprenta.
No hay que pagar licencia por ninguna.

- Escala en px: 12 · 14 · 16 · 20 · 26 · 34 · 46 · 62
- Interlineado: 1.1 en titulares, 1.55 en texto
- Titulares en mayúscula siempre con `letter-spacing: 0.06em`. Apretados se ven
  baratos, y ese es exactamente el efecto contrario al que busca la marca.
- **Precios y tablas**: `font-variant-numeric: tabular-nums`. En un marketplace
  las columnas de cifras que no alinean se leen como descuido.

El logotipo y el eslogan ya están convertidos a curvas: los archivos no dependen
de que nadie tenga las fuentes instaladas.

## Geometría del sistema

- Unidad de espaciado: **8 px** (con medio paso de 4). Todos los márgenes son
  múltiplos.
- Radio de esquina: **4 px** en botones y campos, 8 px en tarjetas. Bajo a
  propósito: el radio grande lee amable y blando, y esta marca necesita leer
  fiable.
- Íconos de interfaz: retícula 24, área viva 20, trazo **2 px**, terminaciones
  **rectas** (`butt` / `miter`), nunca redondeadas. Es la misma decisión que los
  cortes rectos del isotipo, y mezclar terminaciones es lo que hace que un set
  de íconos se vea amateur.

## Aplicación: qué archivo va dónde

**Sitio web** — sube todo el contenido de `dist/web/` a la raíz del dominio
(donde vive `index.html`), y pega en el `<head>` lo que hay en
`dist/web/head-snippet.html`. Eso cubre el favicon en la pestaña, el ícono al
guardar en pantalla de inicio y la instalación como PWA.
En la cabecera del sitio usa `marca/logo-principal.svg` con `<img>`.

**Compartir en redes / WhatsApp** — `dist/social/og-image.png` es la imagen que
aparece cuando alguien pega un enlace de Sendik. Va referenciada en el `<head>`
con `<meta property="og:image">`.

**App iOS** — la carpeta `dist/app/ios/` va al Asset Catalog del proyecto en
Xcode (AppIcon). Ya están sin transparencia, que es requisito de App Store: un
ícono con fondo transparente hace que rechacen la app.

**App Android** — cada `dist/app/android/mipmap-*/` va a la carpeta del mismo
nombre dentro de `app/src/main/res/`. `ic_launcher_foreground.png` es la capa
frontal del ícono adaptativo; el color de fondo de esa capa es `#14162B`.
`play-store-512.png` se sube al formulario de Google Play, no al proyecto.

**Imprenta** (tarjetas, etiquetas, empaque, pendones) — entrega siempre el SVG,
nunca el PNG. Si la imprenta pide un solo color, es `logo-mono-positivo.svg`.
Si va sobre fondo oscuro o sobre foto, `logo-mono-negativo.svg`.

**Desarrollo** — `tokens.css` se pega tal cual en el CSS global; `tokens.json`
es el mismo sistema para herramientas de diseño. No hay que inventar ningún
color ni tamaño que no esté ahí.

## Antes de dar por buena cualquier pieza nueva

1. ¿El bronce aparece una sola vez?
2. ¿Los márgenes son múltiplos de 8?
3. ¿Se lee en blanco y negro puro, sin degradados?
4. ¿Respeta el área de respeto del logo?
5. Quita un elemento. Si la pieza no empeoró, déjalo fuera.
