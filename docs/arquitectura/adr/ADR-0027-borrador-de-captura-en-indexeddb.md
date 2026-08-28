# ADR-0027 — El borrador de captura vive en IndexedDB

**Fecha:** 2026-08-28
**Estado:** aceptada

## Contexto

El criterio 7 de HU-003 dice: «El avance se guarda localmente: cerrar el navegador por
accidente no obliga a empezar de nuevo». La historia pide el comportamiento y no dice
dónde.

Lo que hay que guardar son las tomas ya congeladas y todavía no subidas: el fotograma tal
como sale de la cámara, antes de que el normalizador lo recorte. Se guarda el original y no
el recortado porque el recorte ocurre dentro de la subida —es el mismo camino para la
cámara y para la galería— y adelantarlo aquí obligaría a recortar dos veces. A cambio cada
toma pesa más: un fotograma de 1920 × 1080 a calidad alta ronda el medio megabyte, y son
ocho (RN-017). El peor caso son **varios megabytes de datos binarios**, en un teléfono,
mientras la persona está a mitad de una tarea que le lleva varios minutos.

El proyecto no guardaba nada parecido hasta ahora. Lo único que se guarda en el dispositivo
es la preferencia de tema y la de idioma: dos cadenas cortas.

## Opciones

**`localStorage`.** Es lo que el proyecto ya usa para el tema, así que no estrena nada.
Tiene dos problemas y el segundo es fatal: es **síncrono**, así que escribir varios
megabytes bloquea el hilo principal justo en el asistente que se montó en un worker para no
bloquearlo; y **almacena solo texto**, así que un `Blob` hay que codificarlo en base64, que
crece un tercio. Cinco megas de cuota contra unos cuantos de datos ya inflados: no cabe, y
cuando no cabe lanza.

**No guardar nada y rehacer la secuencia.** Cuesta cero y cumple todo menos el criterio que
lo pide. Se descartó por eso.

**Subir cada toma al servidor en cuanto se captura, y que el borrador del servidor sea el
único estado.** Es tentador porque el borrador ya existe allá y HU-007 ya lo mantiene. Pero
hace la captura inseparable de la red: en un sitio con mala señal —que es donde se
fotografía la ropa, no en una oficina— el asistente se detendría en cada paso a esperar una
subida, y perder la conexión en la toma seis obligaría a repetir de todos modos.

**IndexedDB.** Guarda `Blob` tal cual, sin codificar. Es asíncrona, así que no bloquea. Su
cuota se mide en cientos de megabytes o en un porcentaje del disco, no en cinco. A cambio,
su interfaz es de las más incómodas de la plataforma: peticiones con `onsuccess`, versiones
y transacciones que hay que envolver a mano.

## Decisión

Las tomas del borrador se guardan en **IndexedDB**, en una base propia (`sendik-captura`)
con un almacén de objetos con clave `publicaciónId:posición`. Lo envuelve
`features/listing/infrastructure/capture-draft.store.ts`.

Una toma se borra del borrador en cuanto sube: desde ese momento el servidor es la fuente.

**Todo fallo del almacén se traga y la captura sigue.** No poder guardar no puede impedir
publicar.

## Motivo

**Es la única opción que aguanta el tamaño real del dato.** Las otras dos que guardan o no
caben o bloquean. Unos megabytes en binario son poco para IndexedDB y demasiado para el
almacén de clave y valor, y esa diferencia no es de matiz: `localStorage` **lanza** al
pasarse de cuota, y lo haría a mitad de la secuencia.

**Guardar no puede ser un modo de fallo nuevo.** Una ventana privada, un navegador con el
almacenamiento de sitio bloqueado o un disco lleno hacen que esto no funcione, y las tres
son situaciones normales. Si un fallo al guardar interrumpiera la captura, HU-003 habría
introducido una forma de no poder publicar que antes no existía, a cambio de una comodidad.
Por eso cada método traga su error y sigue: sin borrador, el asistente pide las ocho tomas
de nuevo, que es exactamente como se estaba antes de esta historia.

**Se espera a la transacción, no a la petición.** Es lo que garantiza que al volver del
guardado el dato está en disco. Sin eso, guardar la última toma y cerrar la pestaña acto
seguido perdería justo la que se acababa de tomar, que es el accidente que el criterio 7
existe para cubrir.

**La clave lleva la publicación dentro** porque el borrador es por publicación: quien tiene
dos a medias no puede ver las tomas de una aparecer en la otra.

## Consecuencias

- El proyecto estrena IndexedDB y con ella unas sesenta líneas de envoltorio a mano. No se
  agrega ninguna dependencia por esto: una biblioteca de conveniencia pesaría más que lo
  que se usa de ella, y las promesas ya envuelven lo que hacía falta.
- Quedan datos del vendedor en su propio dispositivo. **No son datos personales de terceros
  ni sensibles**: son fotos de un producto que esa misma persona está a punto de publicar en
  abierto. Aun así cada toma se borra en cuanto sube, para no dejar megabytes olvidados por
  publicación.
- El borrador es por dispositivo y por navegador. Empezar en el teléfono y seguir en el
  computador no funciona, y no se pretende: la captura es un acto con la cámara en la mano.
- Un borrador de una publicación que acabe borrada se queda huérfano hasta que el navegador
  reclame la cuota. Es aceptable mientras el volumen sea de megabytes; si dejara de serlo,
  hay que barrer por antigüedad.

## Cuándo revisar

Si la captura deja de ser el único uso —si el carrito de la Fase 3, o un borrador de
formulario largo, quieren también guardar en el dispositivo—, el envoltorio deja de ser de
`features/listing` y sube a `shared/infrastructure`, como pasó con la cámara (ADR-0026).

Si se mide que la gente empieza a capturar en un aparato y termina en otro, este almacén no
resuelve eso y hay que reabrir la opción de subir cada toma en cuanto se captura.
