# ADR-0022 — Cambio de identidad a Sendik y sustitución del sistema visual

**Fecha:** 2026-08-25 · **Estado:** aceptada

## Contexto

Diseño entregó una identidad nueva completa —marca **Sendik**, con manual, trece
variantes de logo, paquete de iconos y piezas sociales— y un kit de interfaz
construido sobre ella. Sustituye a la identidad anterior (Sastra) entera: no es un
retoque de paleta, cambia el nombre, el símbolo, el eslogan, las dos tipografías
y el papel de cada color.

El repositorio tenía el nombre viejo en 623 archivos: el paquete Java
`co.sastra`, el prefijo de configuración de Spring, los plugins de convención de
Gradle, el prefijo de selectores de Angular, las cookies, el nombre de la base de
datos, el dominio `sastra.co`, los textos de la interfaz, los correos
transaccionales y toda la documentación.

Tres cosas obligaban a decidir y no solo a renombrar.

**El acento cambia de papel.** El sistema anterior usaba el ocre `#D69A3C` como
relleno del botón principal: era «el CTA, uno por pantalla». El manual de Sendik
dice lo contrario y lo dice explícitamente: el bronce va **una vez por pantalla
en lo verificado o garantizado**, y «el botón principal de compra va en tinta, no
en bronce». Mantener el ocre en los botones habría sido conservar la decisión
vieja bajo un color nuevo.

**El bronce tiene dos tonos.** Ninguno de los dos pasa contraste sobre los dos
fondos: `#8A6428` da 4.95:1 sobre fondo claro y `#B4884A` da 5.98:1 sobre fondo
oscuro, pero el claro sobre fondo claro da 2.97:1. El sistema tiene que elegir el
tono por fondo, y hay un caso que el modo claro/oscuro no resuelve: la franja de
tinta del hero y del pie, que es oscura en los dos modos.

**El kit nuevo entrega una hoja, el viejo entregaba tres.** La cadena
`construir.py` del kit anterior parcheaba a mano unos treinta fragmentos del HTML
de la plantilla para producir `tipografia.css` y `marca.css`. Cada parche estaba
atado al texto exacto de esa plantilla, así que la entrega nueva **no se podía
integrar sin rehacer los treinta**.

## Opciones

**Sobre el renombrado.** (a) Cambiar solo lo visible al usuario y dejar
`co.sastra`, los selectores y las cookies con el nombre viejo. Menos riesgo, y el
repositorio queda con dos nombres para siempre. (b) Renombrar todo. Un cambio
grande de una vez, y el compilador y las pruebas lo verifican entero.

**Sobre el acento.** (a) Conservar el ocre en los botones y meter el bronce
además. Habría dado dos acentos por pantalla, que es justo lo que el manual
prohíbe. (b) Mover el botón a tinta y reservar el bronce para la insignia.

**Sobre el kit.** (a) Reescribir `construir.py` contra la plantilla nueva para
seguir generando las tres hojas. (b) Adoptar el generador nuevo tal cual y pasar
`tipografia.css` y `marca.css` a hojas del proyecto.

## Decisión

Renombrado completo a Sendik y dominio `sendik.co`; el bronce sale de los
botones y se reserva a la insignia de vendedor verificado; y el generador nuevo
se adopta tal cual, generando solo `tokens.css`.

## Motivo

**Renombrado completo.** Un repositorio con dos nombres obliga a recordar cuál
usar en cada sitio, y la respuesta es siempre «depende de cuándo se escribió el
archivo». El costo real del cambio grande es bajo aquí: el compilador de Java
verifica los 416 archivos del backend de una vez, y las 446 pruebas del frontend
cubren lo demás. El costo de la opción a) no se paga hoy, se paga cada vez que
alguien abre el proyecto.

**El bronce fuera de los botones.** No es una preferencia estética: es lo que
hace que el bronce signifique algo. Si aparece en el botón de cada pantalla,
cuando aparezca en la insignia de vendedor verificado no dirá nada. La marca
compite por confianza, y la insignia es la traducción visual de esa promesa.

Dentro de la franja de tinta el botón no puede ir en tinta —no se vería— y el
manual prohíbe resolverlo con bronce. La salida es **invertirlo**: la clase
`.franja-tinta` redefine `--color-primario` y `--color-sobre-primario` dentro del
bloque, así que el botón pasa a relleno claro con tinta encima sin que ninguna
pantalla tenga que acordarse.

**El generador nuevo tal cual.** La opción a) conservaba un flujo documentado, y
a cambio garantizaba que la siguiente entrega de diseño volviera a ser
imposible de integrar. La separación entre «lo que genera el kit» y «lo que es
del proyecto» es lo que permite reemplazar `kit_ui.py`, `fuentes.py` y la
plantilla por la versión que llegue sin perder nada propio.

## Consecuencias

- El paquete raíz es `co.sendik`, el prefijo de configuración es `sendik.*`, los
  plugins son `sendik.java-conventions` y `sendik.spring-conventions`, el
  selector de Angular es `sendik-*`, las cookies son `sendik_theme` y
  `sendik_refresh`, y la base de datos local es `sendik`.
- **La cookie de tema cambia de nombre**, así que quien tuviera preferencia
  guardada vuelve a modo claro una vez. La de refresco también: las sesiones
  abiertas contra un despliegue anterior se pierden. En Fase 2 y sin usuarios
  reales, eso no cuesta nada; después habría costado una migración.
- `tipografia.css`, `marca.css` y `fuentes.css` pasan a ser del proyecto y se
  editan en `frontend/src/styles/`. Solo `tokens.css` se genera y se publica.
- Salen **Instrument Sans** e **IBM Plex Mono**; entran **Inter** y **Archivo**,
  variables. Las cifras alinean con `font-variant-numeric: tabular-nums` sobre
  Inter, que es lo que el manual prescribe, así que la tercera familia —que
  existía solo para eso— deja de tener motivo.
- El peso fuerte baja de 700 a 600: es el único que la marca define para Archivo.
- `.regla-puntada` pasa a `.regla-corte` y `.franja-oscura` a `.franja-tinta`.
- **La regla de corte pasa a aparecer una sola vez por pantalla**, en el borde
  superior del pie, que es donde la coloca la maqueta del kit. El manual lo pide
  asi —«una sola vez por pieza»— y la marca anterior no lo pedia: su regla de
  puntada se usaba como separador de secciones. Con la regla nueva, la portada
  mostraba dos y `/como-funciona` llegaba a tres. Se quitaron las de la portada,
  `/como-funciona` y `/preguntas-frecuentes`; la separacion entre bloques la da
  el espaciado. Modifica HU-004 criterio 7 y la nota de diseno de HU-005, y las
  dos quedan anotadas en su archivo.
- El ancho máximo de contenido baja de 1200px a 1140px y el radio grande de 14px
  a 12px, que son las medidas del kit.
- La cabecera móvil baja de 60px a 56px y muestra **solo el isotipo**: el lockup
  mide 169px a 34px de alto y el manual prohíbe usarlo por debajo de 130px.
- `verificar.py` pasa de 18 a 25 pares por modo y ahora lee las hojas **tal como
  las sirve el sitio**, no las copias de `docs/ui/`.
- El eslogan «Compra y vende de forma ágil y segura» resuelve el pendiente más
  grande de `textos-web.md`: el descriptor viejo decía «moda» y el catálogo ya no
  era solo moda. El nuevo no nombra la categoría.
- **El kit entregó las tipografías itálicas.** `fuentes.py` elige el archivo con
  `variables[:1]` sobre el listado de google/fonts, y ahí
  `Inter-Italic[opsz,wght].ttf` ordena antes que `Inter[opsz,wght].ttf` porque el
  guion va antes que el corchete; con Archivo pasa igual. El CSS decía
  `font-style: normal`, el navegador lo respetaba, y el sitio entero se veía
  inclinado porque la inclinación venía dibujada en el archivo. Se sustituyeron
  por los rectos de la API de Google Fonts, que además los da subconjuntados:
  cuatro archivos, 155 KB, frente a los dos de 586 KB del kit. **Está por
  reportar a diseño**: hasta que corrijan `fuentes.py`, ejecutarlo vuelve a meter
  las itálicas. La regresión queda fijada en `frontend/e2e/tipografia.spec.ts`,
  que pinta una letra en un canvas y mide su inclinación real, porque ninguna
  prueba que mire clases o estilos calculados puede ver este fallo.
- **ADR-0010 no se toca.** El kit fija 1:1 para la foto de producto y aquí se
  mantiene 3:4: el razonamiento de esa ADR —prenda colgada, un solo canal de
  imágenes con el visor de ocho tomas, tarjetas de la misma altura— no cambió
  porque cambiara la marca. La divergencia queda anotada en
  `docs/ui/ubicacion-de-activos.md` para que no se lea como descuido.
- El titular del hero y `meta.home.description` **adoptaron el eslogan tal cual**,
  y con ellos `meta.register.description` y `meta.login.description`, que
  arrastraban el mismo descriptor viejo. Se decidió el mismo 25 de agosto de 2026,
  aparte de esta ADR, porque es redacción y no marca: queda registrado en
  `textos-web.md` y reabrió y cerró HU-004. Se descartó la variante de la maqueta
  del kit —«Compra y vende moda y tecnología de forma ágil y segura»—, que el
  propio kit marca como texto de muestra y que vuelve a nombrar las categorías.
  En inglés se reusa la fórmula del pie, `Buy and sell quickly and safely`, para
  que marca y copy no diverjan entre idiomas.
- **`meta.home.title` sigue siendo un marcador de posición.** Dice solo `Sendik`.
  La propuesta de `textos-web.md` nombra una sola de las dos categorías, así que
  no se cambia todavía. No lo bloquea la marca: lo bloquea la redacción.

## Cuándo revisar

Si aparece un caso donde el bronce tenga que ser **texto** sobre una tarjeta en
modo oscuro. Ahí da 4.22:1: cumple como objeto gráfico (WCAG 1.4.11 pide 3:1)
pero no como texto. No se sube el umbral ni se relaja la comprobación: se pide a
diseño un tercer tono, porque el bronce de marca no da.
