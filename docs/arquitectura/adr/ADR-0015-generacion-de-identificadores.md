# ADR-0015 — Generación de identificadores: UUID v7 o v4

**Fecha:** 2026-08-20
**Estado:** aceptada

## Contexto

`docs/arquitectura/modelo-datos.md` fija desde el primer día que la clave primaria
de toda tabla es un `uuid` «generado por la aplicación con UUID v7 (ordenable por
tiempo, buen comportamiento en índices)».

El código no hace eso. Los cinco tipos de identificador de Fase 1 —`UserId`,
`ConsentId`, `RefreshTokenId`, `TokenFamilyId`, `VerificationTokenId`— generan con
`UUID.randomUUID()`, que es v4: aleatorio puro, sin componente temporal. La
desviación apareció al sincronizar documentación con código y no la había
detectado nadie porque no produce ningún fallo: un v4 es un `uuid` válido y todo
funciona igual.

Lo que se pierde es lo que decía el paréntesis del documento. Un v7 lleva los
48 bits altos con el instante de creación, así que las filas nuevas caen juntas al
final del índice; un v4 cae en un punto aleatorio del árbol, lo parte y obliga a
mantener en memoria páginas dispersas. Con cinco tablas pequeñas no se nota. Con
`product_images` a ocho filas por publicación y `payment_events` recibiendo cada
notificación de la pasarela, sí.

Tres restricciones acotan las opciones:

- Java 25 no trae generador de v7 en su biblioteca estándar. `UUID` sabe
  construirse desde dos `long` y sabe generar v4, nada más.
- `domain` solo admite JSpecify (`domain/build.gradle.kts`, ADR-0002), y ahí es
  donde viven los cinco identificadores. Agregar una dependencia a ese módulo
  exige una ADR y contradice la razón de ser del multi-módulo.
- **Hoy no hay nada desplegado** (`docs/operacion/entornos.md`). No existe una
  sola fila en producción.

## Opciones

**A. Generar v7 en `domain`, con biblioteca estándar.** Una fábrica compartida
—del orden de veinte líneas— que compone los 48 bits de milisegundos de
`Instant.now()`, la versión y la variante, y el resto de `SecureRandom`. Sin
dependencia nueva y sin tocar la lista de `domain`. El costo es código propio en
lugar de una implementación de referencia auditada, y que la ordenación dentro de
un mismo milisegundo queda al azar si no se agrega el contador opcional que
describe la RFC 9562. Para ordenar por tiempo en consultas ya está `created_at`;
el contador solo afecta a la localidad de escritura dentro del mismo milisegundo.

**B. Dependencia externa de generación de UUID.** Una implementación probada, con
monotonicidad resuelta. El problema es dónde ponerla: en `domain` no cabe, así que
la fábrica tendría que vivir en `infrastructure` y llegar como puerto. Eso
convierte `UserId.nuevo()` en una colaboración inyectada y obliga a pasar el
generador a todo sitio que cree una entidad. Es un cambio de diseño mayor para
ganar veinte líneas.

**C. Corregir el documento a v4.** Cero trabajo y cero riesgo. Se acepta la
fragmentación de índices y se anota por qué. Tiene un argumento a favor que no es
solo pereza: todas las tablas guardan `created_at`, así que el orden temporal
consultable ya existe y lo que aporta el v7 es localidad de escritura, no una
capacidad nueva.

## Decisión

**Opción A**, aprobada el 21 de agosto de 2026: v7 generado en `domain` con
biblioteca estándar, sin contador de monotonicidad, conservando la decisión que el
modelo de datos ya había tomado.

La fábrica es `co.sastra.shared.id.Uuid7`, con `nuevo()` y `nuevo(Instant)` —la
sobrecarga existe para que la prueba pueda afirmar la marca de tiempo embebida—. La
usan los cinco identificadores de Fase 1 y el `id` de `login_attempts`, que se
generaba en `JdbcLoginAttemptRecorder` y también es clave primaria de una tabla.

### La excepción: las claves de archivo siguen en v4

`ArchivosLocales.claveNueva`, que nombra el archivo de una imagen subida, sigue
generando con `UUID.randomUUID()`. Es el caso que la sección «Cuándo revisar»
aparta, y se aparta desde el primer día en lugar de esperar a que aparezca: de
todos los identificadores del proyecto, la clave de archivo es el único que sale
hacia afuera, porque viaja dentro de la dirección pública de la imagen. Un v7 ahí
publicaría el instante de la subida a cualquiera que vea el enlace, y la localidad
de escritura que se gana no aplica: el nombre de un archivo no es una fila de un
índice.

## Motivo

Porque el momento importa más que la técnica. Cambiar la generación de
identificadores es barato exactamente una vez: mientras no haya filas. Hoy no hay
ninguna. En cuanto exista la primera base desplegada, la tabla queda con dos
generaciones mezcladas para siempre —funciona, pero el beneficio de índice se
diluye en las filas viejas— y la conversación vuelve a abrirse con un costo mayor
y un argumento peor.

Frente a C: el documento no se equivocó, y degradarlo para que coincida con lo que
el código hace por descuido es dejar que un `randomUUID()` escrito sin pensar
decida una propiedad del esquema. Si el v7 no se quisiera, la decisión debería
tomarse con su argumento, no heredarse.

Frente a B: la lista de dependencias de `domain` es corta a propósito y es lo que
ADR-0002 existe para proteger. Convertir la creación de un identificador en un
puerto inyectado, con su interfaz, su implementación y su cableado, es más código
y más acoplamiento que la fábrica que se quiere evitar escribir.

## Consecuencias

Se gana el comportamiento de índice que el modelo de datos prometía, antes de que
haya datos que lo hagan caro.

Se acepta mantener veinte líneas de composición de bits, que se prueban por
comportamiento: que la versión sea 7, que la variante sea la correcta, que dos
identificadores generados en orden comparen en ese orden cuando caen en
milisegundos distintos, y que no se repitan en un lote grande.

Se acepta que dentro del mismo milisegundo el orden es arbitrario. Si algún día
hace falta, el contador de la RFC 9562 se agrega sin cambiar la firma.

Y se acepta que la marca de tiempo va dentro del identificador: quien vea un `id`
sabe cuándo se creó la fila. En estas tablas no es un problema —ninguna expone su
clave primaria a terceros— pero es la razón por la que un v7 no sirve para un
token, y por eso los tokens siguen siendo hash aleatorio y no un `uuid`.

## Cuándo revisar

Si aparece un identificador que sí se publique hacia afuera y donde la marca de
tiempo revele algo sobre el negocio —cuántas publicaciones entran por día, por
ejemplo—, ese tipo concreto vuelve a v4 y se documenta la excepción. La primera ya
existe y está en la decisión: la clave de archivo.

Si el generador propio da un solo problema real, se reabre la opción B con la
evidencia en la mano.
