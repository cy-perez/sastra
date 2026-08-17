# Fuentes

**Los siete archivos ya están aquí.** Se instalaron desde los paquetes
`@fontsource` de npm, que reempaquetan las fuentes oficiales de Google Fonts
subconjuntadas a latín. Versión 5.3.0, licencia OFL-1.1, incluida junto a cada
familia. Peso total: 124 KB.

| Archivo | Familia | Peso | Usado en |
|---|---|---|---|
| `archivo-500.woff2` | Archivo | 500 | `.tipo-h3`, `h3` |
| `archivo-700.woff2` | Archivo | 700 | `.tipo-display`, `.tipo-h1`, `.tipo-h2` |
| `instrument-sans-400.woff2` | Instrument Sans | 400 | Cuerpo, entradilla, secundario |
| `instrument-sans-500.woff2` | Instrument Sans | 500 | Título de tarjeta de producto |
| `instrument-sans-700.woff2` | Instrument Sans | 700 | Etiqueta de botón |
| `ibm-plex-mono-400.woff2` | IBM Plex Mono | 400 | Código de pedido, celdas de cifra |
| `ibm-plex-mono-700.woff2` | IBM Plex Mono | 700 | `.precio` |

Los nombres no se cambian: son los que declara `src/styles/fuentes.css`.

## Si hace falta reinstalarlas

```
npm pack @fontsource/archivo @fontsource/instrument-sans @fontsource/ibm-plex-mono
```

Los `.woff2` salen de `files/<familia>-latin-<peso>-normal.woff2` y se renombran
como la tabla de arriba. También se pueden bajar de fonts.google.com, pero ese
ZIP trae TTF sin subconjuntar y hay que convertirlos:

```
pyftsubset Archivo-Bold.ttf --flavor=woff2 --output-file=archivo-700.woff2 \
  --unicodes=U+0000-00FF,U+0131,U+0152-0153,U+2000-206F,U+20AC,U+2122
```

Alternativa sin descargas: instalar los paquetes como dependencia
(`npm i @fontsource/archivo`) e importar el CSS que traen. Se descartó porque
obliga a aceptar sus `@font-face`, que no coinciden con los respaldos ajustados
de `tipografia.css`.

## Precarga

Solo dos, en el `<head>` del index:

```
<link rel="preload" as="font" type="font/woff2" crossorigin
      href="/fuentes/instrument-sans-400.woff2">
<link rel="preload" as="font" type="font/woff2" crossorigin
      href="/fuentes/archivo-700.woff2">
```

Precargarlas todas compite por el ancho de banda con las fotos de producto y
termina retrasando la página, que es justo lo contrario de lo que se busca. El
atributo `crossorigin` es obligatorio incluso siendo del mismo dominio: sin él el
navegador descarga el archivo dos veces.

## Mientras tanto

Si los archivos no están, el sitio se ve con los respaldos de `tipografia.css`.
Se lee bien y nada se rompe, pero no es la marca. No se publica así.

El logotipo no necesita ninguna de estas fuentes: los SVG ya traen el texto
convertido a curvas.
