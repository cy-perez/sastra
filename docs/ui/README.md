# Sistema de interfaz

Los colores, la tipografía y los componentes de Sastra están definidos, medidos y
son **reconstruibles**: el kit no es un paquete de archivos sueltos, es un
generador con su origen y su verificador.

## El bucle

```
generador/tokens.json          el origen. AQUÍ se cambia un color
generador/fuentes/*.css        marca.css y tipografia.css, escritos a mano
        │
        ├─ construir.py  ──▶  docs/ui/     tokens.css, index.html, contraste.md
        ├─ verificar.py  ──▶  18 pares de contraste en modo claro y en oscuro
        └─ publicar.py   ──▶  frontend/src/styles/   las tres hojas que sirve el sitio
```

Un solo comando hace los tres pasos:

```
cd docs/ui/generador
python3 publicar.py
```

Solo necesita Python 3, nada instalado. Si la verificación falla, no se publica
nada: el frontend se queda con la versión anterior, que al menos es coherente.

## Qué se edita y qué no

| Archivo | Qué es | Se edita |
|---|---|---|
| `generador/tokens.json` | Colores, escala y medidas | **Sí. Es el origen de todo** |
| `generador/fuentes/marca.css` | Componentes de Sastra y correcciones de modo oscuro | **Sí** |
| `generador/fuentes/tipografia.css` | Roles de texto, escala fluida, familias | **Sí** |
| `generador/verificar.py` | Comprueba contraste en los dos modos | Solo la lista `PARES` |
| `generador/kit_ui.py`, `construir.py`, `_plantilla_kit.html` | Entregados por diseño | No |
| `docs/ui/*.css`, `index.html`, `contraste.md`, `LEEME.md` | Salida del generador | No |
| `frontend/src/styles/*.css` | Copia publicada | No |

Las cuatro últimas filas están protegidas por un hook: intentar editarlas
devuelve el archivo correcto que sí se edita. La regla detrás es una sola: **un
color vive en un solo sitio**, y editar la salida es perder el trabajo en el
siguiente build.

La excepción es `frontend/src/styles/fuentes.css`, que es del proyecto y no del
kit: declara los `.woff2` y sí se edita.

## Estructura

```
docs/ui/
  README.md                este archivo
  ubicacion-de-activos.md  dónde vive cada archivo del proyecto
  index.html               guía visual. Se abre en el navegador
  contraste.md             informe WCAG del modo claro
  LEEME.md                 nota de entrega del kit
  tokens.css tipografia.css marca.css      salida, sirve a la guía
  *.svg                    los logos que usa la guía
  generador/               con qué se reconstruye todo lo anterior
```

## Paleta

Tres colores de marca en roles de interfaz. Ninguno es nuevo: cada uno es un
color aprobado del manual puesto en su función.

| Rol | Color | Uso |
|---|---|---|
| Primario | tinta `#16192A` | Estructura, texto, encabezado, botón principal |
| Acento | hilo `#D69A3C` | El CTA. **Uno por pantalla**, siempre como relleno |
| Éxito | verificado | Vendedor verificado, pago liberado, envío entregado |
| Aviso | hilo oscuro | Único añadido de interfaz: la marca no traía color de aviso |
| Error | alerta | Publicación rechazada, pago fallido, error de formulario |

**El ocre nunca es color de texto sobre fondo claro**: da 2.46:1 y es ilegible.
Como relleno de botón con texto tinta encima da 7.1:1.

## Tipografía

`tipografia.css` es la única fuente de verdad del tipo. Ni `tokens.css` ni
`marca.css` definen tamaños de letra, y ningún componente escribe `font-size`
propio: un tamaño de letra fuera de ese archivo es un error.

| Uso | Familia |
|---|---|
| Titulares | Archivo |
| Interfaz y cuerpo | Instrument Sans |
| Precios y códigos | IBM Plex Mono |

**Se aplica el rol, no el tamaño.** Trece clases: `.tipo-display`, `.tipo-h1` a
`.tipo-h3`, `.tipo-titulo-tarjeta`, `.tipo-entradilla`, `.tipo-cuerpo`,
`.tipo-secundario`, `.tipo-leyenda`, `.tipo-etiqueta-boton`, `.precio`,
`.precio-antes`, `.codigo-pedido` y `.celda-cifra`. El nivel del encabezado lo
decide la estructura del documento; el tamaño, la clase. Así un `h2` puede llevar
`.tipo-h3` sin romper la navegación por encabezados de un lector de pantalla.

**Los titulares son fluidos, el cuerpo no.** Los titulares interpolan con
`clamp()` entre 360px y 1280px. El cuerpo se queda fijo en 16px: por debajo, el
navegador móvil hace zoom solo al enfocar un campo y descuadra la maqueta. La
escala usa `rem + vw` y nunca `vw` solo, porque con `vw` solo el titular deja de
responder al zoom.

**Tres pesos: 400, 500 y 700.** Cada peso extra es una descarga más.

**Ancho de lectura: 68 caracteres** (`--medida`).

Los precios van en monoespaciada con `font-variant-numeric: tabular-nums`: es lo
que hace que $ 89.000 y $ 189.000 ocupen el mismo ancho y la columna cuadre. Sin
eso, una lista de precios se lee en zigzag.

## Modo oscuro

Se activa con `data-tema="oscuro"` en el elemento `<html>`. No es una inversión
automática: `marca.css` corrige a mano lo que la derivación no resuelve bien.

Dos cosas que el kit dejó decididas y conviene no deshacer:

- **`.franja-oscura`** para el hero y el pie de página. Redefine `--color-foco`
  dentro del bloque, así que cualquier botón o campo que entre ahí hereda el
  anillo correcto sin que nadie se acuerde. Antes de eso, el foco del CTA
  principal era invisible: anillo tinta sobre franja tinta.
- **`--color-sobre-cta`** es un token propio y no `--color-primario`, porque el
  primario se invierte a claro en modo oscuro y el CTA es ocre en los dos modos.

## Al añadir un componente

Si usa una combinación de colores que no existía, **agrégala a la lista `PARES`
de `verificar.py`**. Un componente que no está en esa lista no se está
comprobando, y ese es el hueco por el que se cuela un texto ilegible.

Si hace falta un color o una medida que no existe, el sistema está incompleto: se
agrega con nombre en `generador/tokens.json` o en `generador/fuentes/marca.css` y
se documenta. Nunca se escribe un valor suelto en un componente.

## Lo que el verificador no comprueba

Colores, no maqueta. No sabe si un botón mide menos de 44px, si una tabla se
desborda en un celular o si el titular se sale de la caja al ampliar el texto.
Eso se ve abriendo `index.html`, cambiando de modo, bajando de 640px, ampliando
el texto al 200% y recorriendo la página entera con el tabulador.

## Pendientes del sistema

- **Medir los tres valores de `size-adjust`** del bloque de respaldo de
  `tipografia.css`, o borrar el bloque. Evitan que la página salte cuando la
  fuente real reemplaza a la del sistema, pero los valores entregados son un
  punto de partida sin medir. Un ajuste inventado empeora el salto.
- **Reemplazar los textos marcados `[muestra]`** de la guía por los definitivos,
  incluidos los datos legales del pie de página.
