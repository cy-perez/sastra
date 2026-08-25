# Generador del kit de Sendik

Esta carpeta es lo que hace que `tokens.css` pueda decir «soy un archivo
generado» y sea verdad. Sin ella, cambiar un color obligaría a editar a mano
justo el archivo que no se debe tocar.

**No necesita nada instalado. Solo Python 3.**

---

## Cambiar un color

```bash
cd docs/ui/generador
# edita tokens.json
python3 publicar.py      # construye, verifica y publica en el frontend
```

O paso a paso, si se quiere ver cada uno:

```bash
python3 construir.py     # reconstruye el kit en la carpeta de arriba
python3 verificar.py     # comprueba el contraste en modo claro y oscuro
```

Los estados —hover, pressed, deshabilitado, anillo de foco, borde de campo y el
color de texto que va sobre cada fondo— **se recalculan solos** desde el color
base, y el modo oscuro también. No se eligen a mano y no hay que tocarlos.

---

## Qué hay aquí

| Archivo | Qué es | ¿Se edita? |
|---|---|---|
| `tokens.json` | Colores, escala y medidas | **Sí. Es el origen de todo** |
| `marca/*.svg` | Los logos que usa la guía | Se reemplazan desde `docs/marca/svg/` |
| `construir.py` | Del proyecto. Arma el kit | Sí, con cuidado |
| `publicar.py` | Del proyecto. Verifica y copia al frontend | Sí, con cuidado |
| `verificar.py` | Del proyecto. Contraste en los dos modos | Al añadir componentes |
| `kit_ui.py` | Deriva los estados y escribe `tokens.css` | **No** |
| `fuentes.py` | Descarga y autoaloja las tipografías | **No** |
| `_plantilla_kit.html` | Esqueleto de la guía | **No** |

Los tres últimos vienen de la caja de herramientas de diseño de interfaz. Se
incluyen aquí a propósito, para que este kit **no dependa de nada externo** y se
pueda reconstruir dentro de cinco años. No se modifican: si hace falta otro paso,
va en `construir.py` o en `publicar.py`, que sí son del proyecto. Así, cuando
diseño entregue una versión nueva del generador, se reemplazan los tres y no se
pierde nada.

Lo que **no** está aquí y antes sí: `fuentes/tipografia.css` y
`fuentes/marca.css`. Esas dos hojas ya no las genera el kit —el de Sendik entrega
solo `tokens.css`— y viven en `frontend/src/styles/`, que es donde se editan.

---

## El bucle

```
tokens.json  ──construir.py──▶  tokens.css      (generado, NO se edita)
                                index.html       (la guía visual)
                                contraste.md     (informe del modo claro)
                                tipografia.md    (familias, pesos, licencia)
                                LEEME.md         (cómo regenerarlo)
                                      │
                                verificar.py ──▶ claro y oscuro, 25 pares cada uno
                                      │
                                publicar.py  ──▶ frontend/src/styles/tokens.css
```

**Si se edita `tokens.css` a mano, el siguiente `construir.py` borra el cambio.**
No es un capricho: el punto del sistema es que un color viva en un solo sitio.

---

## Las tipografías

Los cuatro `.woff2` —Inter y Archivo, variables, en subconjunto latino y latino
extendido— están versionados en `../fuentes/`. `construir.py` no los descarga: se
limita a cambiar el `<link>` de la guía para que los use en vez de Google Fonts.

⚠️ **No los generó `fuentes.py`, y no debe volver a generarlos hasta que diseño
lo corrija.** Ese script elige el archivo con `variables[:1]` sobre el listado de
google/fonts, y ahí `Inter-Italic[opsz,wght].ttf` ordena antes que
`Inter[opsz,wght].ttf` porque el guion va antes que el corchete. Con Archivo pasa
igual. El kit se entregó con las dos itálicas y el sitio entero se veía
inclinado. Los archivos actuales salieron de la API de Google Fonts, que los da
rectos y ya subconjuntados: 155 KB frente a 586 KB.

Si se ejecuta `python3 fuentes.py tokens.json --out ../fuentes` sin ese arreglo,
las itálicas vuelven. Lo caza el caso `no hay texto en italica` de
`frontend/e2e/tipografia.spec.ts`, que mide la inclinación real de la letra
pintada.

---

## Por qué hay dos verificadores y no uno

`kit_ui.py` escribe `contraste.md`, pero **solo comprueba el modo claro** y solo
los pares que el generador conoce. El sistema real de Sendik tiene tres cosas que
él no ve:

- **La franja de tinta** (`--color-tinta`), que no cambia con el modo y por eso
  necesita sus propios pares en los dos.
- **El bronce con dos tonos**, uno por fondo. Cruzarlos es el error que el manual
  marca como prohibido, y solo se detecta comparando cada tono con su fondo.
- **Los componentes propios y las correcciones de modo oscuro** de
  `frontend/src/styles/marca.css`, que el kit no tiene delante.

`verificar.py` cubre ese hueco: lee los colores reales de las tres hojas **tal
como las sirve el sitio** —`docs/ui/tokens.css` recién generado más las dos del
proyecto— arma la paleta de cada modo y comprueba 25 pares en cada uno.

Lo que encontró al integrar el kit de Sendik: el bronce `#B4884A` da **4.22:1
sobre la tarjeta en modo oscuro**. Cumple de sobra como objeto gráfico —que es lo
único que es: una línea de 2px y un icono de 16px, WCAG 1.4.11— pero **no llega
al umbral de texto**. Si alguna vez hace falta el bronce como texto sobre una
tarjeta oscura, no se sube el umbral: se pide a diseño un tercer tono, porque el
bronce de marca no da.

---

## Al añadir un componente nuevo

Si el componente usa una combinación de colores que no existía, **añádela a la
lista `PARES` de `verificar.py`**, con el umbral que le corresponda: 4.5:1 si es
texto, 3:1 si es un objeto gráfico o el borde de un control. Un componente que no
está en esa lista no se está comprobando, y ese es justo el hueco por el que se
cuela un texto ilegible.

---

## Lo que esto no comprueba

`verificar.py` mira **colores, no maqueta**. No sabe si un botón mide menos de
44px, si una tabla se desborda en un celular o si el titular se sale de la caja
al ampliar el texto. Eso hay que verlo:

1. Abre `../index.html` en el navegador.
2. Pulsa «Cambiar modo».
3. Reduce la ventana por debajo de 640px.
4. Amplía el texto al 200% (`Ctrl` o `Cmd` con `+`).
5. Recorre la página entera con el tabulador y comprueba que **siempre se ve
   dónde está el foco**.

Los problemas de una maqueta se ven, no se deducen.
