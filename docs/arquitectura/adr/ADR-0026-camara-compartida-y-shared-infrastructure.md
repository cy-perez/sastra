# ADR-0026 — La cámara sube a `shared` y la nitidez se queda

**Fecha:** 2026-08-28
**Estado:** aceptada

## Contexto

HU-003 necesita la cámara del dispositivo desde `features/listing`, para el asistente de
ocho tomas. Ya existe una, escrita para HU-002, en
`features/seller-verification/infrastructure/camera.service.ts`: abre `getUserMedia`, la
apaga al salir y congela un fotograma.

`frontend/CLAUDE.md` no deja la salida fácil: **«`features/x` no importa de `features/y`.
Lo compartido sube a `shared` o `core`»**. Así que o sube, o se duplica.

Y no sube limpia. `CameraService.capturar()` devuelve hoy un `Fotograma` con dos cosas: el
JPEG que se sube y una copia reducida en grises con la que HU-002 mide el desenfoque, para
no dejar subir una cédula ilegible. Ese segundo dato **no lo pide ningún criterio de
HU-003**: el asistente de producto se guarda del temblor y la inclinación con el nivel de
los 5 grados (criterio 3), que es otra cosa y vive en otro sitio.

La estructura documentada de `shared` tiene tres carpetas —`ui/`, `domain/`,
`directives/` y `pipes/`— y ninguna es para un servicio que habla con el aparato.

## Opciones

**Duplicar el acceso a cámara en `features/listing`.** No rompe ninguna regla de
importación y no toca código de HU-002, que está probado y funcionando. Deja dos
envoltorios de `getUserMedia` y dos formas de apagar el flujo; el día que una se arregle,
la otra no.

**Subir `CameraService` entera, con la medida de nitidez.** Un movimiento y ya. Deja en
`shared` —lo que es de todos— una regla que es de una sola funcionalidad, y obliga a
`features/listing` a recibir en cada toma un cálculo de varianza del laplaciano que nunca
mira. Cuando algo en `shared` solo lo usa uno, `shared` deja de querer decir nada.

**Partirla: el acceso al aparato sube, la regla se queda.** Dos ficheros donde había uno,
y hay que tocar las pruebas de HU-002. A cambio, cada pieza queda donde se decide lo que
hace.

## Decisión

`CameraService` sube a **`shared/infrastructure/`** reducida al acceso al aparato, y
`capturar()` pasa a devolver un `Blob`. La medida de nitidez se queda en
`features/seller-verification/infrastructure/sharpness.service.ts`, que la calcula sobre
ese `Blob` y sigue apoyándose en `domain/blur.ts`, que no se movió.

Con ella se abre `shared/infrastructure/`, la cuarta carpeta de `shared`, para los
servicios que hablan con el navegador y no con la red: la cámara, el acelerómetro y el
normalizador de fotos.

## Motivo

**La regla de importación entre funcionalidades no es negociable y la duplicación tiene
fecha de caducidad.** Dos envoltorios de `getUserMedia` empiezan idénticos y divergen en el
primer arreglo que alguien haga en uno solo. Apagar el flujo al salir —para que el
indicador del dispositivo no se quede encendido después de subir una cédula— es exactamente
la clase de detalle que se arregla una vez y se olvida en la copia.

**Lo que sube a `shared` es lo que no tiene dueño.** Abrir la cámara y congelar un
fotograma no decide nada del producto: lo hacen igual una selfie y la toma frontal de una
chaqueta. Que una cédula borrosa no se pueda leer sí es una decisión, y es de la
verificación de identidad. Subir las dos juntas habría puesto en el vocabulario común una
regla que solo entiende una pantalla.

**Devolver un `Blob` iguala las dos entradas de imagen del proyecto.** Es lo que hizo la
decisión barata: una foto elegida desde la galería **también es un `Blob`**, así que la
cámara y el selector de archivos entran por el mismo sitio y pasan por el mismo
normalizador. Eso es lo que el criterio 8 de HU-003 pide —«se ofrece carga desde galería
con el mismo recorte forzado»— y sale de la forma del tipo, no de un `if`.

Se paga una decodificación de más: la nitidez ahora recibe el JPEG y lo vuelve a abrir,
donde antes recibía los píxeles de camino. Ocurre una vez por foto, dentro de un gesto de
la persona que ya está esperando, y compra que HU-003 no arrastre un cálculo que no usa.

**`shared/infrastructure/` y no `core/`.** `core` es lo transversal que se carga una vez y
que la aplicación entera necesita: la configuración, los interceptores, el idioma, el tema.
Una cámara no la necesita la aplicación entera; la necesitan dos funcionalidades. Esa es
justo la definición de `shared` que ya está escrita.

## Consecuencias

- `frontend/CLAUDE.md` documenta la carpeta nueva. Sin eso, el siguiente servicio de
  navegador acaba en `core` o duplicado, que es lo que esta decisión evita.
- Las pruebas de HU-002 cambiaron de doble: donde había una cámara falsa que devolvía
  píxeles medidos, hay una cámara falsa que devuelve un `Blob` y una medida falsa que dice
  que sí o que no. **Son mejores pruebas**: antes, comprobar que una foto borrosa no se
  emite obligaba a construir a mano una imagen en grises con franjas de un píxel; ahora se
  dice que la medida no pasa. El umbral en sí lo sigue probando `blur.spec.ts` sobre la
  función pura, que es donde vive la decisión.
- `capturar()` deja de usar un `<canvas>` colgado del documento y usa `OffscreenCanvas`. El
  lienzo nunca se pinta, así que tocar el DOM para tirarlo acto seguido no aportaba nada, y
  rompía el renderizado en servidor. Es además el mismo lienzo que usa el worker del
  normalizador: el proyecto tiene una sola forma de dibujar una foto.
- Se acepta que HU-002 quede tocada por una historia que no es la suya. Es el costo de
  haber escrito la cámara dentro de una funcionalidad cuando ya se sabía que HU-003 la iba
  a querer; la alternativa era pagarlo con dos copias.

## Cuándo revisar

Si una tercera funcionalidad necesita la cámara con exigencias distintas —enfoque manual,
elegir entre varias lentes, grabar vídeo—, `CameraService` dejará de ser el mínimo común y
habrá que decidir si crece o si se parte por caso de uso.

Si `shared/infrastructure/` pasa de unos pocos servicios de navegador y empieza a recibir
adaptadores de red, la carpeta habrá dejado de significar lo que aquí se decidió y hay que
volver a partirla.
