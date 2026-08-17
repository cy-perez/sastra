# Kit de interfaz web — Sastra

Lo marcado con ⚠️ hay que confirmarlo antes de programar.

---

## Resumen

**Marca:** Sastra · **Negocio:** marketplace colombiano donde cualquiera compra y
vende moda, nueva y usada, con la plataforma como respaldo de la transacción.

**Acción principal del sitio:** publicar una prenda ⚠️
**Personalidad:** seria y formal · equilibrada en color · tradicional y de confianza
**Plataforma:** sin definir ⚠️

### Archivos de marca recibidos

| Archivo | Recibido | Observación |
|---|---|---|
| Logo principal SVG | sí | Idéntico al horizontal |
| Horizontal | sí | El que se usa en el header |
| Isotipo | sí | Header en móvil y favicon |
| Mono negativo | sí | Footer y modo oscuro |
| Isotipo negativo | sí | |
| HEX del manual | sí | Con contrastes ya verificados |
| Manual de marca | sí | Área de respeto y mínimos incluidos |

**Sin limitaciones por archivos faltantes.** Llegó todo lo necesario en vector, así
que los colores son exactos y no aproximados.

---

## Decisiones de diseño

**Paleta** — origen: manual de marca, **verificado contra los colores reales del
vector** con `extraer_paleta.py`. Cuando hay manual manda el manual, pero la
comprobación vale la pena: se extrajeron los HEX de los SVG del logo, el isotipo,
el banner y las piezas de redes, y coinciden exactamente con los tres del manual
(`#16192A`, `#D69A3C`, `#FFFFFF`). No hay deriva entre lo documentado y lo
dibujado. **Ningún color de la interfaz es nuevo**; cada uno es un color aprobado
puesto en su rol.

| Rol de interfaz | HEX | Color de marca | Por qué |
|---|---|---|---|
| primario | `#16192A` | tinta | Estructura, texto, enlaces, botón secundario, header |
| acento | `#D69A3C` | hilo | El CTA. Uno por pantalla, siempre relleno con texto tinta |
| fondo | `#F7F5F1` | hueso | Fondo de página |
| superficie | `#FFFFFF` | superficie | Tarjetas de producto |
| texto | `#16192A` | tinta | |
| texto suave | `#5B6072` | texto-suave | Metadatos, precio tachado |
| éxito | `#1F7A55` | verificado | Vendedor verificado, pago liberado, envío entregado |
| aviso | `#8A5A12` | hilo-oscuro | **Único añadido**: la marca no traía color de aviso |
| error | `#B3402A` | alerta | Publicación rechazada, error de formulario |

**Por qué el primario no es el ocre.** En las piezas de redes el ocre ocupa el
50% de la superficie, así que un reparto de roles por peso lo nombraría primario
— y de hecho el extractor lo sugiere así. Sería un error: el ocre funciona como
relleno de botón (con texto tinta encima da 7.1:1), pero como **color de texto
sobre fondo claro da 2.46:1 y es ilegible**. Por eso la estructura la lleva el
tinta y el ocre queda reservado para un solo elemento por pantalla. Ocupar mucha
superficie en una pieza gráfica y ser el color principal de una interfaz son dos
cosas distintas.

### Tipografía

Tres familias, y cada una tiene un trabajo. Las tres son SIL OFL: no cuestan nada
ahora ni cuando el negocio crezca, y se pueden empaquetar dentro de la app sin
permiso de nadie.

| Familia | Para qué | Por qué esa |
|---|---|---|
| **Archivo** | Titulares | Condensada y de asta firme: sostiene los tamaños grandes sin volverse decorativa. Es la del logotipo, así que el titular y la marca riman |
| **Instrument Sans** | Interfaz y cuerpo | Neutra y de altura de x generosa, que es lo que se lee cómodo a 16px en celular |
| **IBM Plex Mono** | Cifras | Numerales tabulares: los precios se alinean en columna y el catálogo se recorre rápido |

La regla general es dos familias como máximo. **La tercera entra solo porque hay
cifras que alinear**, y ese es el único motivo que la justifica.

**Trece roles, no trece tamaños.** Se aplica el rol (`.tipo-h2`,
`.tipo-titulo-tarjeta`, `.precio`…), no el tamaño. El nivel de encabezado lo
decide la estructura del documento; el tamaño lo decide la clase. Así nunca hay
que bajar de `<h2>` a `<h3>` solo para que algo se vea más pequeño, que es lo que
rompe la navegación por encabezados de un lector de pantalla.

**Los titulares son fluidos, el cuerpo no.** Los titulares interpolan entre 360px
y 1280px de ancho con `clamp()`: uno de 62px ocupa media pantalla en un celular, y
uno de 34px se ve flojo en escritorio. El cuerpo se queda fijo en **16px**: por
debajo, el navegador móvil hace zoom solo al enfocar un campo y descuadra la
maqueta. La escala usa `rem + vw` y nunca `vw` solo, porque con `vw` solo el
titular deja de responder al zoom del navegador.

**Pesos: tres y no más** — 400, 500 y 700. Cada peso extra es una descarga más, y
en celular con datos móviles se nota.

**Ancho de lectura:** 68 caracteres (`--medida`). Más largo y el ojo pierde el
renglón al saltar de línea.

**Radio:** 8px el general (4 pequeño, 14 grande). Neutro funcional, que es lo que
pide una marca seria y tradicional: 0 sería severo e institucional, 16+ sería
blando y juvenil.

**Rasgo propio:** la **regla de puntada** del manual (guion 16, hueco 9) separa las
secciones. Es el único elemento decorativo y es lo que hace que el sitio se
reconozca como de la misma familia que las piezas de redes. No la sustituyas por
una línea continua.

---

## Entregables

```
kit-sastra/
├── index.html          guía visual completa — ábrela en el navegador
├── tokens.css          variables generadas, con modo oscuro
├── tipografia.css      roles de texto, escala fluida y carga de fuentes
├── marca.css           componentes de Sastra y correcciones de modo oscuro
├── contraste.md        informe WCAG del modo claro
├── LEEME.md            esta nota
├── *.svg               los logos que usa la guía
└── generador/          CON QUÉ SE RECONSTRUYE TODO LO DE ARRIBA
    ├── README.md       cómo cambiar un color
    ├── tokens.json     el origen de todo. AQUÍ se edita
    ├── construir.py    arma el kit
    ├── verificar.py    contraste en modo claro Y oscuro
    ├── kit_ui.py       deriva los estados y escribe tokens.css
    ├── _plantilla_kit.html
    ├── fuentes/        tipografia.css, marca.css y esta nota
    └── marca/          los SVG del logo
```

### Cambiar un color

```bash
cd generador
# edita tokens.json
python3 construir.py     # reconstruye el kit
python3 verificar.py     # comprueba contraste en los dos modos
```

Solo hace falta Python 3, nada instalado. Los estados —hover, pressed, foco,
borde de campo, color de texto sobre cada fondo— **se recalculan solos**.

**Las tres hojas se cargan en este orden y no son intercambiables.** Los
originales de `tipografia.css`, `marca.css` y esta nota viven en
`generador/fuentes/`: si editas la copia de arriba, el siguiente `construir.py`
la sobrescribe.

- `tokens.css` **es generado y no se edita a mano.** Los estados (hover, pressed,
  foco, borde de campo, color de texto sobre cada fondo) se calculan desde el
  color base. Si hay que cambiar un color, se cambia en `tokens.json` y se vuelve
  a generar, o el próximo build borra el ajuste.
- `tipografia.css` **es la única fuente de verdad del tipo.** Todos los tamaños,
  familias, interlineados y numerales viven ahí; ni `tokens.css` ni `marca.css`
  redefinen texto. Si un tamaño de letra aparece en otro sitio, es un error.
- `marca.css` **sí se edita** (el original está en `generador/fuentes/`). Ahí viven los componentes propios de Sastra y las
  correcciones de modo oscuro.

---

## Qué se corrigió y por qué (no era cosmético)

1. **Modo oscuro incompleto.** El generador solo deriva fondo, superficie, texto,
   borde y primario. Los tres colores semánticos se quedaban con su valor de modo
   claro y daban **2.2–2.5:1** sobre fondo oscuro; el anillo de foco quedaba en
   tinta, invisible; el borde de campo se quedaba en 4.26:1. Reemplazados por los
   valores oscuros del manual.
2. **El rojo de modo oscuro del manual** (`#E8735A`) da 4.38:1, a 0.12 del umbral.
   Aclarado un 4% hasta `#E97961` (4.59:1), sin cambio perceptible.
3. **El logo desaparecía en oscuro.** Va en tinta, y sobre fondo tinta no se ve.
   Montado el par de logos con alternancia por CSS (`.logo-sitio`).
4. **Hero y footer se invertían a claro** en modo oscuro, y ahí el logo blanco del
   footer desaparecía. Resuelto con el token `--fondo-oscuro-marca`, fijo en
   ambos modos.
5. **Las tablas desbordaban a 360px.** Envueltas en un contenedor con
   desplazamiento horizontal, enfocable con teclado.
6. **Enlaces del pie de página de 30px de alto.** Subidos a 44px, igual que los
   de navegación.
7. **Con el texto al 200%, los titulares desbordaban.** El mínimo de `clamp()`
   está en `rem`, así que al ampliar también se duplica, y una palabra larga se
   salía de la caja. Resuelto con guion automático y corte de palabra.
8. **La rejilla de productos estaba fijada en píxeles**, así que al 200% mantenía
   dos tarjetas de 148px y el precio se cortaba. Pasada a `rem`: ahora colapsa a
   una columna cuando el texto se amplía.
9. **Faltaba el generador.** `tokens.css` decía ser un archivo generado y no
   venía con qué generarlo, así que cambiar un rol de color obligaba a editar a
   mano justo el archivo que no se debe tocar. Ahora va en `generador/`, y es
   autocontenido: no depende de ninguna herramienta externa.
10. **El CTA traía un color suelto.** `.btn-cta` tomaba su texto de
    `--color-primario`, que se invierte a claro en modo oscuro, y eso se
    parcheaba con un `#0C0F1B` escrito a mano — el hex suelto que este sistema
    prohíbe. Ahora hay un token propio, `--color-sobre-cta`, con un solo valor
    para los dos modos.
11. **El anillo de foco del CTA principal era invisible.** En modo claro el
    anillo es tinta y la franja del hero también es tinta. Nadie lo había visto:
    la revisión de contraste solo miraba texto, no el anillo contra su fondo.
    Resuelto con la clase `.franja-oscura`, que redefine `--color-foco` dentro
    del bloque para que cualquier control que entre ahí herede el anillo bueno.

---

## Verificación

**Informe de contraste: sin fallas.** Además del informe estático se midió el
contraste real de **cada texto renderizado contra su fondo efectivo**, en modo
claro y oscuro: 0 textos por debajo del umbral que les corresponde.

| Comprobación | Resultado |
|---|---|
| Colores del kit contra los HEX reales de los SVG | coinciden exactamente |
| Informe de contraste | sin fallas |
| Contraste real medido en pantalla, ambos modos | 0 fallas |
| Foco visible en todo lo interactivo | 3px, tinta en claro y ocre en oscuro |
| Destinos táctiles | todos ≥ 44px |
| Maqueta a 360px de ancho | sin desbordes |
| Texto al 200% | sin desbordes a 1280px ni a 360px |
| Escala fluida | titular 34px a 360px → 62px a 1280px; cuerpo fijo en 16px |
| Numerales tabulares en precios | activos |
| Modo oscuro con el logo | correcto |
| Un H1 por página, sin saltos de nivel | correcto |
| Etiquetas de formulario y `alt` de imágenes | completos |
| Contraste de tokens en modo claro y oscuro | 18 pares cada uno, sin fallas |
| El kit se reconstruye desde `tokens.json` | sí, salida idéntica byte a byte |
| Error de formulario con `aria-describedby` | correcto |

---

## Para quien programa

1. Copia `tokens.css`, `tipografia.css` y `marca.css` al proyecto y enlázalos
   **en ese orden**, antes de tus estilos. `generador/` no se sube al sitio: es
   la herramienta, no el producto.
2. Usa siempre variables. **Ningún HEX ni píxel suelto.** Si necesitas un color
   que no está, el sistema está incompleto: añádelo con nombre en `marca.css`.
3. Modo oscuro: `data-tema="oscuro"` en el `<html>`. Para respetar la preferencia
   del sistema:
   ```js
   document.documentElement.dataset.tema =
     matchMedia('(prefers-color-scheme: dark)').matches ? 'oscuro' : 'claro';
   ```
4. **Header:** 72px en escritorio, 60px en móvil, logo a 34px, **fijo** al hacer
   scroll con fondo sólido. En móvil entra el isotipo (el manual fija 24px como
   mínimo del lockup, y con el buscador al lado no cabe legible).
5. **Menú móvil:** a pantalla completa, enlaces de 44px, botón de cierre de 44px.
6. **Barra fija abajo solo en la ficha de producto**, con el botón de comprar. En
   el resto del sitio no: ocupa espacio permanente y no se justifica.
7. **Ancho máximo de contenido:** 1200px.
8. **Puntos de quiebre:** móvil <640 · tableta 640–1024 · escritorio >1024.
9. **Tablas en móvil:** contenedor con desplazamiento horizontal, no reducir la
   tipografía.
10. **Fuentes.** Para empezar, Google Fonts basta y es lo que trae la guía. Para
    producción conviene autoalojar los `.woff2`: quita una conexión a un tercero
    y mejora la carga. **Precarga solo dos archivos** (Instrument Sans 400 y
    Archivo 700); precargarlo todo compite con las fotos de producto y retrasa la
    página. El `<link rel="preload">` exacto está en la guía.
11. **Favicon y manifiesto:** ya vienen resueltos en `dist/web/` del paquete de
    marca. Sube esos archivos a la raíz y pega `head-snippet.html` en el `<head>`.

### Imágenes que debe entregar el cliente ⚠️

| Uso | Proporción | Tamaño mínimo |
|---|---|---|
| Foto de producto | **3:4** (vertical) | 900 × 1200 px |
| Foto de portada de categoría | 16:9 | 1600 × 900 px |

La proporción 3:4 **no es negociable**: si cada foto llega con la suya, la
rejilla de productos queda con tarjetas de alturas distintas. Conviene forzarla
en el formulario de publicación, recortando al subir.

---

## Textos de muestra

Todo lo marcado **[muestra]** en la guía es relleno y hay que reemplazarlo. Los
textos definitivos salen de la skill `copywriter-web`. Incluye el titular del
hero, las tres tarjetas de confianza, el NIT y la dirección del footer.

---

## Pendientes antes de publicar

- [ ] ⚠️ **Confirmar el CTA principal.** Puse *"Publicar prenda"* en ocre: un
      marketplace sin inventario no arranca, así que el acento está en la oferta.
      Si tu prioridad ahora es la demanda, se cambia en un sitio.
- [ ] ⚠️ **Confirmar la plataforma** (WordPress, Shopify, a medida). Cambia cómo
      se integran los tokens, no el diseño.
- [ ] ⚠️ **Datos reales del footer**: razón social, NIT y dirección. En Colombia
      son señal de que la empresa existe, y con formulario en el sitio el enlace
      a la política de tratamiento de datos es obligatorio y visible.
- [ ] ⚠️ **Medios de pago reales** que se van a ofrecer (PSE, Nequi, tarjetas,
      contraentrega). Transmiten más confianza que cualquier sello genérico.
- [ ] Reemplazar los textos de muestra por los definitivos.
- [ ] Entregar las fotos en 3:4.
- [ ] Cargar las tres tipografías, autoalojadas o desde Google Fonts.
- [ ] ⚠️ **Medir los valores de `size-adjust`** del bloque de respaldo en
      `tipografia.css`, o borrar el bloque. Sirven para que la página no salte
      cuando la fuente real reemplaza a la del sistema, pero **los tres valores
      que dejé son un punto de partida y no están medidos** contra los archivos
      reales. Un ajuste inventado empeora el salto en vez de arreglarlo.

---

## Lo que este kit no cubre

- **Los textos** del sitio: skill `copywriter-web`.
- **Los artículos del blog**: skill `redactor-de-contenidos`.
- **Piezas publicitarias** impresas o para redes: skill `diseno-publicitario`.
- **Pantallas de la app móvil**: este kit cubre la web. Los tokens sirven de base,
  pero los patrones de navegación de una app son otros.
