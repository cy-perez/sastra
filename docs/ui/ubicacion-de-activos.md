# Dónde vive cada archivo de marca y de interfaz

Los paquetes de marca y de interfaz traen tres clases de archivo y cada una va a
un sitio distinto. Confundirlas es el error habitual: terminan 40 PNG dentro del
build de producción y el favicon devolviendo 404.

| Clase | Ejemplo | Dónde |
|---|---|---|
| Código que compila | `tokens.css`, `tipografia.css`, `marca.css` | `frontend/src/styles/` |
| Activo que el navegador pide por URL | favicons, manifiesto, logos SVG | `frontend/public/` |
| Documentación y original | manual, informe de contraste, el generador | `docs/` |

## Ya ubicado en el andamiaje

```
frontend/src/styles/
  fuentes.css     @font-face de los archivos reales. Lo agrega el proyecto
  tokens.css      copia publicada. NO se edita: la sobrescribe publicar.py
  tipografia.css  copia publicada. Se edita en docs/ui/generador/fuentes/
  marca.css       copia publicada. Se edita en docs/ui/generador/fuentes/
frontend/src/styles.css   importa los cuatro, en ese orden

frontend/public/
  favicon.ico, favicon.svg, apple-touch-icon.png, favicon-*.png
  icon-192.png, icon-512.png, icon-512-maskable.png, site.webmanifest
  og-image.png, twitter-card.png
  logo-horizontal.svg, logo-mono-negativo.svg, isotipo.svg, isotipo-negativo.svg
  fuentes/        los siete .woff2 y sus licencias

docs/ui/     el kit construido: index.html (guía visual), contraste.md, las tres
             hojas, los SVG que usa la guía, LEEME.md y este archivo
docs/ui/generador/   el origen: tokens.json, fuentes/ editables, construir.py,
             verificar.py, publicar.py y kit_ui.py
docs/marca/  manual.md, tokens de marca, revision-visual.html, el paquete de
             identidad completo y los generadores en herramientas/
```

En Angular, todo lo que está en `public/` se sirve desde la raíz del sitio. Por
eso el favicon se pide como `/favicon.ico` y el logo se usa como
`<img src="/logo-horizontal.svg" alt="Sastra">`. El logo no se redibuja en
código: son 4KB de vector y se ve nítido en cualquier pantalla.

## Ojo: hay dos `tokens` y no son lo mismo

El sistema tiene dos niveles y cada uno tiene su archivo. Confundirlos rompe la
paleta entera.

| Archivo | Qué contiene | Se carga en el sitio |
|---|---|---|
| `docs/marca/tokens.json` | Los colores aprobados con nombre de marca: tinta, hilo, hueso | No |
| `docs/marca/tokens.css` | Los mismos, como variables. Referencia para piezas gráficas y para la app | No |
| `docs/ui/generador/tokens.json` | El mapa de roles: qué color de marca cumple cada función de interfaz. **Aquí se edita** | No |
| `docs/ui/tokens.css` | Salida del generador, con los estados derivados | No |
| `frontend/src/styles/tokens.css` | Copia publicada de la anterior | Sí |

La cadena va en un solo sentido:

```
docs/marca/tokens.json            colores aprobados (tinta, hilo, hueso)
        ↓
docs/ui/generador/tokens.json     mapa de roles (primario, acento, exito, error)
        ↓  construir.py
docs/ui/tokens.css                variables finales, con hover y pressed derivados
        ↓  publicar.py
frontend/src/styles/tokens.css    lo que sirve el sitio
```

Cada eslabón hacia abajo lo escribe un script. Los dos últimos son **salida**: si
se editan a mano, la siguiente ejecución los sobrescribe y el trabajo se pierde.

Un color nuevo se agrega arriba y baja. Nunca al revés, y nunca se escribe
directo en el archivo final: es generado.

El único añadido que hizo la interfaz sobre la marca es el color de aviso, que la
marca no tenía, resuelto con el ocre oscuro por ser el único legible sobre fondo
claro.

## Copias, y cuál manda

Las tres hojas existen dos veces por necesidad: Angular no puede servir nada
desde `docs/`. La de `docs/ui/` es la salida del generador; la de
`frontend/src/styles/` es la que se sirve. **Ninguna de las dos se edita.**

El original editable está en `docs/ui/generador/fuentes/`, y el flujo completo es
un solo comando:

```
cd docs/ui/generador
python3 publicar.py
```

Encadena construir, verificar y copiar. Si la verificación de contraste falla, no
publica nada: el frontend se queda con la versión anterior, que al menos es
coherente.

El hook `proteger-archivos.mjs` bloquea la escritura sobre las cinco copias
generadas y sobre todo `docs/marca/`, y en el mensaje indica dónde se edita de
verdad.

**La excepción son los binarios.** Los favicons, los iconos y los cuatro SVG que
usa la web sí están dos veces: el original en `docs/marca/dist/`, que es el
archivo, y la copia en `frontend/public/`, que es lo que el servidor entrega. No
hay forma de que Angular sirva algo desde `docs/`. Si algún día cambia el logo,
se regenera el paquete con `docs/marca/herramientas/` y se vuelven a copiar los
mismos catorce archivos; están listados arriba para que la copia sea mecánica.

## Ya no falta nada

Los dos huecos que arrastraba el andamiaje están cerrados: `kit_ui.py` llegó con
la última entrega del kit, y los siete `.woff2` se instalaron desde los paquetes
`@fontsource` de npm, que reempaquetan las fuentes oficiales subconjuntadas a
latín. 124 KB en total, licencia OFL-1.1 incluida junto a cada familia.

El sistema visual completo —color, tipografía y componentes— se puede reconstruir
sin depender de nadie.

## Falta en el head

El paquete trae `docs/marca/dist/web/head-snippet.html`. Su contenido va en el
`<head>` del `index.html` del frontend, junto con las etiquetas Open Graph que
apuntan a `/og-image.png` y `/twitter-card.png`.

## Lo que NO va al frontend

Se queda en `docs/marca/` como archivo: los iconos de iOS y Android
(`dist/app/`), las piezas de redes distintas de og-image y twitter-card
(`dist/social/`), los PNG de respaldo del logo (`dist/raster/`) y los SVG que no
usa la web (vertical, mono positivo, isotipo-app, piezas de campaña).

Los iconos de aplicación se necesitarán cuando exista la app móvil. Copiarlos
ahora al frontend solo agrega peso al repositorio y confusión sobre cuál es el
bueno.

## Las fuentes

El kit define cómo se usa el tipo pero no incluye los archivos. Los siete
`.woff2` ya están instalados en `frontend/public/fuentes/`, con los nombres que
declara `fuentes.css`. Cómo reinstalarlos y las dos etiquetas de precarga están
en el LEEME de esa carpeta.

Se autoalojan, no se cargan desde el CDN de Google: una petición menos a un
tercero, un punto de fallo menos y ningún dato del visitante saliendo del sitio.
Para una demostración rápida, `docs/ui/index.html` usa el CDN y se abre
directamente en el navegador, sin descargar nada.
