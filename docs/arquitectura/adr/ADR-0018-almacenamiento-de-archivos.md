# ADR-0018 — Almacenamiento de archivos: dos almacenes y subida por el backend

**Fecha:** 2026-08-20
**Estado:** aceptada

> **Implementada entera el 21 de agosto de 2026.** La dependencia
> `com.google.cloud:google-cloud-storage` quedó aprobada ese día, fijada en la
> versión 2.69.0, y con ella entraron `GcsPublicFileStore`, `GcsRestrictedFileStore`
> y el cliente de `GcsWiring`. Los puertos, la validación y el adaptador local ya
> estaban.
>
> Los dos adaptadores conviven y los elige `sastra.storage.provider`: `local` para
> desarrollo y para las dos suites de pruebas, que no deben depender de una cuenta de
> nube ni de que haya red; `gcs` contra los cubos de verdad. Mientras el sitio no se
> despliegue, esa es la integración que pide `entornos.md`: local contra la capa
> gratuita.

## Contexto

Tres cosas del producto están bloqueadas por lo mismo: no existe ningún puerto de
almacenamiento de archivos.

- El criterio 21 de HU-001, la foto de perfil, que se dejó fuera de la Fase 1
  citando exactamente este bloqueo.
- HU-002, que necesita el documento de identidad por ambas caras y una selfie.
- HU-003, que necesita ocho tomas por prenda.

Lo ya decidido y que no se reabre aquí:

| Fuente | Restricción |
|---|---|
| ADR-0009 | Imágenes en Cloud Storage. **Sin el almacenamiento de Vercel**, dicho explícitamente |
| RN-046 | Cédula, selfie y cuenta bancaria cifradas, solo las ve el proceso de verificación, **nunca salen en una respuesta de la API** |
| `datos-personales.md` | Documentos y selfies a almacenamiento privado, nunca público. Los registros nunca contienen la imagen de una selfie |
| RN-018, RN-019 | Recorte 3:4 en el cliente antes de subir; mínimo 900 × 1200 px |
| ADR-0015 | Identificadores UUID v7 |

## Opciones

### Cuántos puertos

**Uno solo, con un parámetro de visibilidad.** Menos código y un solo adaptador.
Costo: la diferencia entre publicar una toma de producto y publicar una cédula
queda a un argumento de distancia. Un enum mal pasado, o un valor por omisión mal
elegido, publica el documento de identidad de alguien. Ese error debería ser
imposible de escribir, no algo que se detecta en revisión.

**Dos puertos separados.** Más superficie. A cambio, el tipo dice a dónde va el
archivo, y no hay forma de expresar «guarda esta cédula donde cualquiera la vea».

### Cómo llega el archivo

**Por el backend.** El archivo pasa por Cloud Run, que valida los bytes antes de
guardarlos. Es lo único que permite comprobar el tipo real y quitar el EXIF
**antes** de que el archivo exista en el almacén. Costo: dobla el tráfico, ocupa
la petición mientras sube, y Cloud Run limita la petición a 32 MB.

**URL firmada y subida directa.** Más barato y escala mejor, sobre todo con ocho
tomas por prenda. Costo: la validación solo puede ocurrir cuando el archivo ya
está guardado, lo que obliga a una máquina de estados —subido, validado,
rechazado— y a un proceso que limpie lo que no pase. Y mientras un archivo está
en «subido sin validar», existe en el almacén algo que nadie ha comprobado.

## Decisión

**Dos almacenes** —`PublicFileStore` y `RestrictedFileStore`, nombrados en el
glosario— y **subida a través del backend**.

Los adaptadores son dos por almacén: sistema de archivos local para desarrollo y
pruebas, y Cloud Storage para la nube.

## Motivo

Los dos puertos, porque la diferencia entre los dos almacenes es de garantías y no
de configuración. Un archivo público es cacheable y va por CDN; uno reservado es
cifrado, auditado y nunca se sirve por una dirección pública. Cuando la única
diferencia entre los dos caminos es un argumento, el camino equivocado se toma
tarde o temprano, y aquí equivocarse significa publicar la cédula de alguien.

La subida por el backend, porque en Fase 2 el volumen no es el problema y la
validación sí. Las imágenes llegan ya recortadas a 3:4 desde el cliente (RN-018),
así que 32 MB sobra con holgura. Y hay tres comprobaciones que solo tienen sentido
antes de guardar:

1. **El tipo real, por los bytes de cabecera y no por la extensión ni por el
   `Content-Type`.** Los dos los pone quien sube. Un `.jpg` que en realidad es un
   HTML con un script, servido desde el dominio del sitio, es un XSS almacenado.
2. **El EXIF se quita.** Lleva coordenadas GPS: una toma de producto publicada con
   su EXIF dice dónde vive el vendedor. Aplica también a la selfie.
3. **Las dimensiones mínimas de RN-019**, que se comprueban en el cliente pero no
   se pueden confiar al cliente.

Con URL firmada, las tres pasan a ocurrir después, sobre un archivo que ya existe.

**Nombres opacos.** La clave se deriva de un UUID v7 (ADR-0015), nunca del nombre
original ni de nada de la persona. Un nombre adivinable en un almacén público
convierte «privado por no estar enlazado» en «público con un paso extra», y el
nombre original que trae un archivo es entrada del usuario como cualquier otra.

**Qué significa «cifradas» en RN-046.** Cloud Storage cifra en reposo por omisión,
con claves gestionadas por Google. Se acepta eso como cumplimiento de RN-046 en la
etapa de prototipo, y **no** se agrega cifrado en la aplicación. El motivo es que
cifrar en la aplicación obliga a custodiar y rotar una clave propia, y una clave
mal custodiada es peor que la de Google bien custodiada. Lo que sí se hace desde el
principio es lo que de verdad protege esos archivos: almacén separado, sin acceso
público, y una cuenta de servicio que solo puede leerlos desde el proceso de
verificación.

**El borrado entra ahora, no después.** Cerrar la cuenta ya anonimiza los datos de
la base; a partir de aquí tiene que borrar además los archivos de la persona. Un
derecho de eliminación que deja la selfie en un bucket no es un derecho de
eliminación (Ley 1581, `datos-personales.md`).

## Consecuencias

Lo que se gana:

- Se desbloquean el criterio 21 de HU-001, HU-002 y HU-003 con una sola rebanada.
- El tipo impide expresar el error grave.
- La validación ocurre antes de que el archivo exista, así que no hay estado
  intermedio que limpiar.

Lo que se acepta perder:

- **Una dependencia nueva y grande**, `google-cloud-storage`, que además ata el
  adaptador a un proveedor. Queda contenida en `infrastructure` y detrás del
  puerto: cambiar de proveedor es escribir otro adaptador, no tocar el dominio.
- **El tráfico de subida se paga dos veces**, hacia Cloud Run y de ahí al almacén.
- **La petición se ocupa mientras sube.** Con ocho tomas por prenda esto puede
  volverse incómodo, y es lo primero que habrá que medir en HU-003.
- **Cloud Run limita la petición a 32 MB.** Hoy sobra; si algún día hay que aceptar
  vídeo, no.
- El adaptador local guarda en el sistema de archivos, que en Cloud Run es efímero.
  Es solo para desarrollo y pruebas, y el perfil de la nube nunca lo usa.
- **Los nombres de los cubos son configuración, no constantes.** El nombre de un cubo
  es único en todo Google, así que el que se quiera puede estar tomado. A cambio
  aparece un error nuevo posible —los dos cubos apuntando al mismo sitio— que no da
  ningún síntoma y publicaría la cédula de quien se verifique: por eso la aplicación
  no arranca si son iguales.
- **El objeto público se guarda con caché de un año e `immutable`.** La clave es un
  identificador aleatorio que no se reutiliza, así que el contenido de una dirección
  no puede cambiar, solo dejar de existir. Sin esa cabecera, cada visita al catálogo
  vuelve a descargar imágenes que el navegador ya tenía.

## Cuándo revisar

- **Si HU-003 mide que subir ocho tomas por el backend es lento o caro.** Entonces
  tiene sentido la URL firmada para el almacén público, con la máquina de estados
  que exige. El almacén reservado se queda por el backend en cualquier caso: son
  pocos archivos y la validación importa más que el costo.
- **Si aparece un requisito de residencia de datos**, porque eso cambia el almacén
  y puede cambiar el proveedor.
- **Si RN-046 se endurece** y alguien exige claves propias, entra CMEK con Cloud
  KMS antes que cifrado en la aplicación.
