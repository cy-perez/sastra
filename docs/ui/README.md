# Sistema de interfaz

Los colores, la tipografía y los componentes de Sendik están definidos, medidos y
son **reconstruibles**: el kit no es un paquete de archivos sueltos, es un
generador con su origen y su verificador.

## El bucle

```
generador/tokens.json          el origen. AQUÍ se cambia un color
        │
        ├─ construir.py  ──▶  docs/ui/   tokens.css, index.html, contraste.md,
        │                                 tipografia.md, LEEME.md
        ├─ verificar.py  ──▶  25 pares de contraste en modo claro y en oscuro,
        │                     leyendo las hojas que sirve el sitio de verdad
        └─ publicar.py   ──▶  frontend/src/styles/tokens.css
```

Un solo comando hace los tres pasos:

```
cd docs/ui/generador
python3 publicar.py
```

Solo necesita Python 3, nada instalado. Si la verificación falla, no se publica
nada: el frontend se queda con la versión anterior, que al menos es coherente.

## Una hoja generada y tres del proyecto

El kit de Sendik entrega **`tokens.css` y nada más**. `tipografia.css`,
`marca.css` y `fuentes.css` son del proyecto: viven en `frontend/src/styles/` y
se editan ahí mismo.

Antes no era así. El kit anterior entregaba las tres hojas acopladas, y
`construir.py` tenía que parchear a mano unos treinta fragmentos de la plantilla
para adaptarlas. Eso hacía que **una entrega nueva de diseño no se pudiera
integrar**: cada parche estaba atado al texto exacto de la plantilla vieja. Al
separar lo generado de lo propio, `kit_ui.py` y la plantilla se reemplazan por la
versión que llegue y no se pierde nada del proyecto.

## Qué se edita y qué no

| Archivo | Qué es | Se edita |
|---|---|---|
| `generador/tokens.json` | Colores, escala y medidas | **Sí. Es el origen de todo** |
| `frontend/src/styles/marca.css` | Componentes de Sendik y correcciones de modo oscuro | **Sí** |
| `frontend/src/styles/tipografia.css` | Roles de texto, escala fluida, familias | **Sí** |
| `frontend/src/styles/fuentes.css` | Los `@font-face` de los `.woff2` del sitio | **Sí** |
| `generador/construir.py`, `publicar.py` | Del proyecto: arman y publican | Sí, con cuidado |
| `generador/verificar.py` | Comprueba contraste en los dos modos | Solo la lista `PARES` |
| `generador/kit_ui.py`, `fuentes.py`, `_plantilla_kit.html` | Entregados por diseño | No |
| `docs/ui/tokens.css`, `index.html`, `contraste.md`, `tipografia.md`, `LEEME.md` | Salida del generador | No |
| `frontend/src/styles/tokens.css` | Copia publicada | No |
| `docs/marca/**` | Entregable cerrado de identidad | No |

Las últimas cuatro filas están protegidas por el hook `proteger-archivos.mjs`:
intentar editarlas devuelve el mensaje con el sitio donde se edita de verdad.

## Las tres reglas de marca que el sistema hace cumplir

Están en el manual (`../marca/manual.md`) y no son preferencias:

1. **El bronce aparece una vez por pantalla**, y siempre en lo verificado o
   garantizado. En el sitio eso es la insignia de vendedor verificado y nada
   más. El botón principal va en tinta, no en bronce.
2. **El bronce tiene dos tonos y no se cruzan.** `#8A6428` solo sobre fondo
   claro, `#B4884A` solo sobre fondo oscuro; cruzarlos da 2.97:1. `tokens.css`
   alterna el correcto según el modo, y `marca.css` resuelve el caso que el modo
   no alcanza: la franja de tinta, que es oscura en los dos.
3. **El corte del isotipo se repite una sola vez por pieza**, como `.regla-corte`
   o como el borde superior de la insignia. Nunca las dos en la misma pantalla.

## Los documentos de esta carpeta

| Archivo | Qué es |
|---|---|
| `index.html` | La guía visual. Ábrela en el navegador |
| `ENTREGA.md` | La nota de entrega de diseño, con sus pendientes marcados ⚠️ |
| `LEEME.md` | Cómo regenerar el kit. Lo escribe el generador |
| `tipografia.md` | Familias, pesos, licencia e instalación. Generado |
| `contraste.md` | Informe WCAG del modo claro. Generado |
| `accesibilidad.md` | La lista que se recorre antes de dar una pantalla por buena |
| `ubicacion-de-activos.md` | Dónde vive cada archivo y por qué |
| `fuentes/`, `fuentes.css` | Las tipografías autoalojadas, tal como llegaron |
