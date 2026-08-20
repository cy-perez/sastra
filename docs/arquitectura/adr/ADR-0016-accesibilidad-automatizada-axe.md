# ADR-0016 — Auditoría de accesibilidad automatizada con axe-core

**Fecha:** 2026-08-20 · **Estado:** aceptada

## Contexto

HU-004 y HU-005 cerraron con el mismo asterisco en su lista de pruebas
requeridas: «accesibilidad automatizada sobre las páginas, en modo claro y
oscuro. **Pendiente**». No estaba pendiente por olvido, sino porque hacía falta
un motor de auditoría y agregar una dependencia exige decidirlo antes.

Lo que hoy sí se comprueba, en `frontend/e2e/contenido.spec.ts` y en las pruebas
de componente, se escribió a mano regla por regla: un solo `h1`, sin saltos de
nivel de encabezado, sin desplazamiento horizontal a 360px, destinos táctiles de
44px y recorrido completo por teclado. Cubre lo que alguien se acordó de
escribir. No cubre lo que nadie pensó: un `aria-labelledby` que apunta a un
identificador que ya no existe, un campo que perdió su etiqueta al refactorizar,
un contraste que se rompió al ajustar un token del modo oscuro.

El sitio informativo es la primera pantalla de un marketplace que todavía no ha
lanzado. Cada página nueva de Fase 2 en adelante multiplica la superficie, y las
comprobaciones escritas a mano no escalan a ese ritmo.

## Opciones

**`@axe-core/playwright`.** El motor de axe inyectado en la página dentro de la
suite de Playwright que ya existe. Reutiliza el servidor de renderizado, el
navegador y el paso de integración continua que ya están montados: una
dependencia de desarrollo y ningún proceso nuevo. Audita el DOM ya hidratado,
que es el que usa la persona.

**Lighthouse CI.** Da accesibilidad, rendimiento y SEO en una sola herramienta,
y su informe se comparte bien. A cambio es un segundo motor con su propio
arranque de navegador y su propia configuración de servidor, y su puntaje de
accesibilidad es un subconjunto de axe presentado como número. Un 96 no dice qué
falta; una lista de violaciones sí.

**pa11y.** Motor propio sobre HTML_CodeSniffer, orientado a auditar direcciones
desde la línea de órdenes. Levantaría el sitio por su cuenta, duplicando el
arranque que Playwright ya hace, y no comparte contexto con las pruebas
existentes: fijar el modo oscuro por cookie sería configuración aparte.

**Seguir a mano.** Costo cero hoy y una regla nueva escrita por cada fallo que ya
se coló. Es lo que hay, y es la razón de esta ADR.

## Decisión

`@axe-core/playwright` como dependencia de desarrollo del frontend, ejecutándose
dentro de la suite de extremo a extremo que ya corre en cada integración.

## Motivo

Es la opción que no agrega infraestructura. El servidor de renderizado, el
Chromium, el paso de integración continua y la forma de fijar el tema por cookie
ya existen y están resueltos; axe entra como una aserción más dentro de eso. Las
otras dos traen su propio arranque para hacer un trabajo que la suite actual ya
sabe hacer.

Frente a Lighthouse pesa la forma de la salida. Lo que sirve para corregir un
fallo es la regla que se violó y el selector del nodo que la viola, y eso es lo
que devuelve axe. Un puntaje agregado obliga a abrir el informe para saber qué
pasó, y en integración continua nadie lo abre.

Auditar el DOM hidratado y no el HTML servido es deliberado. El HTML del
servidor ya tiene sus propias pruebas, que son las de ADR-0006; lo que puede
romper la accesibilidad —un atributo que escribe un componente, un estado que
cambia al abrir un menú— solo existe después de hidratar.

**Un motor automático detecta una parte de los problemas, no todos.** Es la
limitación conocida de este tipo de herramienta, y conviene dejarla escrita para
que la suite en verde no se lea como «el sitio es accesible». No detecta si el
orden de lectura tiene sentido, si el texto de un enlace significa algo fuera de
su contexto, si el mensaje de error explica cómo corregir el campo, ni si la
página funciona de verdad con un lector de pantalla. Eso se revisa a mano y
seguirá revisándose a mano.

## Consecuencias

- Una dependencia de desarrollo más en el frontend. No viaja al paquete que se
  sirve: solo la usan las pruebas.
- Las pruebas de extremo a extremo tardan más. Son dos auditorías por página,
  claro y oscuro, sobre las once páginas públicas.
- **Ninguna regla se desactiva para que la suite pase.** Una violación se
  corrige en el código. Si alguna regla no aplica de verdad a un caso, se
  desactiva en ese caso y con el motivo escrito al lado, nunca en la
  configuración global.
- Una versión nueva de axe puede encontrar violaciones que la anterior no veía,
  y romper una suite que estaba en verde sin que nadie tocara el sitio. Es
  deseable: significa que el motor mejoró. Pero obliga a que actualizar la
  dependencia sea una tarea con su propio espacio, no un arrastre dentro de otra.
- Cada página pública nueva entra a la lista de rutas auditadas. Si no entra, no
  se audita, y ese es el modo de fallo a vigilar.

## Cuándo revisar

Si la auditoría empieza a dominar el tiempo de la integración continua, momento
en el que la salida es separarla a su propio trabajo en paralelo antes que
recortar la cobertura. También si hiciera falta medir rendimiento en la
canalización, porque entonces Lighthouse entra por otra puerta y habría que
decidir si absorbe también la accesibilidad —la respuesta previsible es que no,
por lo dicho sobre la forma de la salida.
