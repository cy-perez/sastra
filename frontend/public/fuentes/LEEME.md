# Fuentes

Dos familias y cuatro archivos: cada familia viene en dos subconjuntos, latino y
latino extendido, y el navegador baja el que necesite según el `unicode-range`
que declara `src/styles/fuentes.css`.

| Archivo | Familia | Subconjunto | Peso | Usado en |
|---|---|---|---|---|
| `inter-latin.woff2` | Inter | latino | 47 KB | Cuerpo, interfaz, formularios, botones, precios |
| `inter-latin-ext.woff2` | Inter | latino ext. | 83 KB | Lo mismo, cuando aparece un carácter fuera de Latin-1 |
| `archivo-latin.woff2` | Archivo | latino | 13 KB | Titulares y precios grandes |
| `archivo-latin-ext.woff2` | Archivo | latino ext. | 12 KB | Lo mismo, fuera de Latin-1 |

Los nombres no se cambian: son los que declara `src/styles/fuentes.css`.

Dos familias y no tres. La marca solo define Archivo e Inter, y los precios se
alinean con `font-variant-numeric: tabular-nums` sobre Inter, no con una familia
monoespaciada aparte (manual de marca, sección Tipografía).

## Pesos

Son fuentes variables: un archivo cubre todo el rango. El sistema usa **tres
pesos y no más**: 400, 500 y 600, declarados como `--peso-regular`,
`--peso-medio` y `--peso-fuerte` en `tokens.css`. Archivo solo se usa en 600, que
es el único que la marca define para ella.

Si un diseño pide un peso que no está en esa lista, se ajusta el diseño, no la
lista: cada peso que se usa de verdad es una decisión tipográfica más que
sostener, aunque el archivo ya lo traiga dentro.

## ⚠️ Estos archivos NO son los del kit

El kit de Sendik llegó con las variantes **itálicas** de las dos familias
(`inter-variable.woff2`, `archivo-variable.woff2`). El CSS declaraba
`font-style: normal`, el navegador lo respetaba, y aun así el sitio entero se
veía inclinado: la inclinación venía dibujada dentro del archivo.

El defecto está en `docs/ui/generador/fuentes.py`, que elige el archivo así:

```python
variables = [a for a in archivos if "[" in a]
elegidos = variables[:1] if variables else ...
```

En `google/fonts`, `Inter-Italic[opsz,wght].ttf` ordena **antes** que
`Inter[opsz,wght].ttf`, porque el guion va antes que el corchete. Con Archivo
pasa igual. Hay que reportarlo a diseño: `fuentes.py` es un script entregado y no
se modifica desde aquí, así que **mientras el kit no se corrija, cualquiera que
lo ejecute vuelve a meter las itálicas**.

El caso `no hay texto en italica` de `e2e/tipografia.spec.ts` pinta una letra en
un canvas y mide su inclinación real, así que si vuelve a pasar, la suite falla.

## De dónde salieron entonces

De la API de Google Fonts, que entrega los mismos archivos oficiales, en versión
recta y además ya subconjuntados:

```
https://fonts.googleapis.com/css2?family=Inter:wght@400..600&family=Archivo:wght@600&display=swap
```

De esa hoja se toman las URL de los bloques `/* latin */` y `/* latin-ext */` de
cada familia, junto con los `unicode-range` que las acompañan.

Eso resuelve de paso el otro problema del kit: sus dos archivos sumaban **586 KB**
sin subconjuntar. Estos cuatro suman **155 KB**, y en una visita normal solo se
bajan los dos latinos, 60 KB.

## Precarga

Solo los dos latinos, en el `<head>` del index:

```
<link rel="preload" as="font" type="font/woff2" crossorigin
      href="/fuentes/inter-latin.woff2">
<link rel="preload" as="font" type="font/woff2" crossorigin
      href="/fuentes/archivo-latin.woff2">
```

Los extendidos no se precargan: el navegador solo los pide si aparece un carácter
que los necesite, y adelantar 95 KB que casi nunca se pintan competiría con las
fotos de producto. El atributo `crossorigin` es obligatorio incluso siendo del
mismo dominio: sin él el navegador descarga el archivo dos veces.

## Mientras tanto

Si los archivos no están, el sitio se ve con los respaldos de `tipografia.css`.
Se lee bien y nada se rompe, pero no es la marca. No se publica así: el caso
`los cuatro archivos de fuente responden` lo comprueba.

El logotipo no necesita ninguna de estas fuentes: los SVG ya traen el texto
convertido a curvas.
