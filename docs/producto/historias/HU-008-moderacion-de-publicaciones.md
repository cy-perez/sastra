# HU-008 — Moderación de publicaciones

**Fase:** 2 | **Estado:** implementada y revisada el 27 de agosto de 2026.
Las tres pruebas de extremo a extremo que quedaron en rojo al implementarla destaparon un
defecto de HU-007 y ya están en verde; el diagnóstico está al final.
**Reglas que aplica:** RN-015, RN-020, RN-022, RN-024, RN-045, RN-061, RN-063

## Objetivo

Una persona de Sendik con rol de moderador puede ver las publicaciones que
esperan revisión, mirar sus tomas y sus datos, y aprobarlas o rechazarlas con
motivo, desde la interfaz y sin tocar la base de datos.

## Por qué ahora

HU-007 está terminada y **apagada**. Los diecisiete endpoints existen y están
probados, y desde el 26 de agosto de 2026 también la interfaz del vendedor: se
puede crear un borrador, subir las ocho tomas y enviarlo a revisión.

Y ahí se acaba. **Nada puede salir de `PENDING_REVIEW`**, porque las tres
decisiones del moderador —`approval`, `rejection`, `removal`— solo se alcanzan con
una llamada directa a la API. Mientras siga así, `FEATURE_PUBLISHING` no se puede
encender: se le pediría a un vendedor que fotografíe ocho veces su prenda para
dejarla en una cola que no atiende nadie.

Esta historia es lo que convierte HU-007 en algo que se puede poner en producción.
Es el mismo corte que hubo entre HU-002 y HU-006, y por el mismo motivo.

## Alcance

Entra:

- Bandeja de publicaciones en `PENDING_REVIEW`, la más vieja primero.
- Detalle de una publicación con sus datos, sus medidas y sus ocho tomas.
- Las marcas de atención de RN-020, visibles y explicadas.
- Aprobar.
- Rechazar con motivo de la lista cerrada y nota opcional.
- Protección de la ruta por rol.

No entra:

- **Bajar una publicación ya visible** (`POST /{id}/removal`, RN-024). El endpoint
  existe, pero actúa sobre algo `PUBLISHED` o `PAUSED`, y la bandeja solo devuelve
  lo pendiente: no hay forma de llegar a ese identificador desde la interfaz.
  Ofrecerlo exigiría un buscador de publicaciones vivas, es decir el catálogo o un
  endpoint nuevo, y eso no es «falta la interfaz». Es exactamente el argumento con
  el que HU-006 dejó fuera revocar el sello, y se resuelve igual: con su historia.
- Historial de lo ya decidido. La bandeja muestra lo pendiente y nada más. El
  rastro existe en la bitácora de moderación (RN-045) pero no tiene lectura, y
  dársela es decidir qué ventana de tiempo abarca y paginarla de verdad.
- Editar la publicación por el moderador. No existe y no debe: quien corrige es el
  vendedor, y por eso el rechazo lleva motivo (RN-022).
- Paginación de verdad. La bandeja pide página y tamaño; **un tamaño por encima de 50 se
  rechaza con 400**, no se recorta en silencio. Con el volumen de la fase eso sobra.
- Métricas de moderación: cuántas se rechazan y por qué. Fase 4.

## Criterios de aceptación

1. Dado un usuario con el rol `MODERATOR`, cuando abre la ruta de la bandeja,
   entonces ve las publicaciones en `PENDING_REVIEW` ordenadas de la que lleva más
   tiempo esperando a la más reciente.

2. Dado un usuario **sin** el rol `MODERATOR` —con sesión o sin ella—, cuando
   intenta abrir la ruta, entonces no ve ningún dato de ninguna publicación y se le
   lleva a una pantalla que no revela que la bandeja exista.

3. Dado que `FEATURE_PUBLISHING` está apagada, cuando alguien llama al endpoint de
   la bandeja, entonces recibe **404 y no 403**: con la funcionalidad apagada la
   ruta no existe, y un 403 confirmaría que sí.

4. Dado que la bandeja está vacía, cuando el moderador la abre, entonces ve el
   estado vacío del sistema y no una tabla sin filas.

5. Dado que la bandeja está cargando, cuando todavía no hay respuesta, entonces se
   muestra el esqueleto de `marca.css`; y si la petición falla, un estado de error
   con la acción de reintentar.

6. Dada una publicación marcada por RN-020 —precio fuera de rango— o por una toma
   cargada desde la galería, cuando aparece en la bandeja, entonces se distingue de
   las demás **con texto y no solo con color**, y en el detalle se dice cuál es la
   marca.

7. Dado el detalle de una publicación, cuando el moderador lo abre, entonces ve el
   título, la descripción, la marca, la categoría, la condición, la talla, las
   medidas, el color, el precio y las ocho tomas, y desde cuándo espera.

8. Dado el detalle de una publicación, cuando el moderador aprueba, entonces pasa a
   `PUBLISHED`, desaparece de la bandeja, se anota en la bitácora quién decidió
   (RN-045) y él vuelve a la lista con la confirmación de lo que hizo.

9. Dado el detalle de una publicación, cuando el moderador rechaza, entonces debe
   elegir uno de los siete motivos de `ListingRejectionReason`; la nota es opcional
   y no puede pasar de 500 caracteres. Sin motivo elegido, la acción no se puede
   enviar.

10. Dado que el moderador ha elegido aprobar o rechazar, cuando confirma, entonces
    la decisión se le pide confirmar una vez: son actos que notifican por correo al
    vendedor y no se deshacen desde la interfaz.

11. Dada una publicación que ya fue resuelta por otro moderador, cuando este intenta
    decidir sobre ella, entonces se le dice que ya no está pendiente y la bandeja se
    refresca. No se muestra un error genérico.

12. Dado un moderador con una publicación propia en revisión, cuando intenta
    aprobarla o rechazarla, entonces se rechaza la operación **en el backend** y la
    interfaz no ofrece la acción (RN-063).

13. Dada una publicación que el vendedor retira de revisión mientras el moderador la
    tiene abierta, cuando este decide, entonces recibe el mismo trato del criterio
    11: ya no está pendiente.

14. Dado el modo oscuro y una ventana de 360px, cuando se recorre la bandeja y el
    detalle, entonces todo es legible, operable con teclado y sin desbordamiento
    horizontal.

## Casos borde

- **Dos moderadores sobre la misma publicación.** El segundo llega tarde: criterio
  11. La carrera la resuelve el backend con las transiciones de RN-061 y el bloqueo
  optimista sobre `version`, no la interfaz.
- **El vendedor retira mientras se revisa.** RN-061 permite volver de
  `PENDING_REVIEW` a `DRAFT` por voluntad del vendedor, así que la publicación puede
  desaparecer de la cola sin que nadie decida. Criterio 13.
- **El precio cambia mientras espera.** `cambiarPrecio` solo exige que la publicación
  no sea terminal, así que sí ocurre. No la saca de la cola ni altera su turno —de eso
  se encarga `submitted_at`— pero **puede cambiar su marca de atención**: un precio que
  entra en rango la quita, uno que sale la pone. El detalle muestra lo que hay al
  abrirlo, no lo que había al enviarse.
- **Una toma que no está.** El archivo puede faltar por un fallo de despliegue. Se
  dice, y la publicación sigue siendo decidible: rechazar por fotos inservibles es una
  respuesta válida.
- **La sesión caduca con el detalle abierto.** El token de acceso vive 15 minutos y
  revisar ocho tomas puede llevar más. Lo que no puede pasar es perder una nota ya
  escrita.
- **Nota con datos de un tercero.** La nota viaja al vendedor por correo. La regla la
  impone quien escribe, no una validación; la pantalla lo recuerda donde se escribe.
- **Volver atrás con el navegador** después de decidir. No debe reenviar la decisión.
- **Recargar el detalle** con la dirección directa: tiene que funcionar, incluido el
  rol, sin pasar por la lista.

## Diseño

Pantalla interna, como la de HU-006: **no lleva el acento bronce**, que está reservado
a la acción principal de las pantallas de cara al comprador. Aprobar y rechazar se
distinguen por forma y texto, no por color.

- Lista: una fila por publicación con el título, el precio, el tiempo esperando y la
  marca de atención si la hay. Destinos táctiles de 44px.
- Detalle: los datos y las medidas arriba, las ocho tomas debajo. **Las tomas se cargan
  con la pantalla**, al revés que en HU-006: aquí no son datos personales, están en el
  almacén público y mirarlas es justo el trabajo. No hay bitácora de lectura que
  proteger, solo la de decisiones.
- Las tomas se muestran con `NgOptimizedImage`, la primera con `priority`, igual que en
  el listado del vendedor.
- Estados de carga, vacío y error con las clases del sistema (`.esqueleto`, `.vacio`).
- En móvil la lista es una pila de tarjetas, no una tabla con desplazamiento horizontal.
- La marca de atención del criterio 6 se anuncia con etiqueta de texto además del color,
  y en el detalle con su motivo.

## Notas técnicas

**Las tres decisiones ya existen y esta historia no las toca.** RN-063 ya está
comprobada en el caso de uso y tiene su código de error.

| Método y ruta | Uso en esta historia |
|---|---|
| `GET /api/v1/moderation/listings?pagina=0&tamano=20` | **Nuevo.** La bandeja |
| `GET /api/v1/listings/{id}` | El detalle. Ya responde la forma completa al moderador |
| `POST /api/v1/listings/{id}/approval` | Sin cuerpo |
| `POST /api/v1/listings/{id}/rejection` | `{ reason, note? }`, `note` hasta 500 |
| `POST /api/v1/listings/{id}/removal` | **No se usa aquí.** Fuera de alcance |

**Por qué la bandeja no cuelga de `/listings`.** El contrato reserva
`GET /api/v1/listings` para el catálogo público, paginado **por cursor**
(`contrato-api.md`). Esta es una lista administrativa y acotada, que el mismo contrato
permite paginar por página y tamaño. Mezclarlas en una ruta obligaría a que la
autorización dependa de un parámetro, que es donde se cuelan los huecos. Va en
`/api/v1/moderation/listings`, espacio propio.

**El endpoint responde una fila, no la publicación entera.** Como
`PendingVerificationResponse` en HU-006: identificador, título, precio, estado, desde
cuándo espera, si necesita atención y por qué, la toma frontal para reconocerla, y
**`own`**. Ese booleano es la lección que HU-006 tuvo que aprender implementando: sin él
la mitad de interfaz del criterio 12 no se puede cumplir, porque la pantalla no tendría
cómo saber que la publicación es del propio moderador y este se enteraría después de
pulsar. Se manda un booleano y no el identificador del vendedor.

**El cambio de esquema que sí trae esta historia: `submitted_at`.** Hoy no existe marca
de cuándo una publicación entró a revisión. `enviarARevision` solo toca `updatedAt`, y
`cambiarPrecio` también lo toca sin sacar la publicación de la cola, así que ordenar por
`updatedAt` haría que tocar el precio retrasara la propia revisión. Es un incentivo
torcido y además una mentira en pantalla: «espera desde hace» se reiniciaría. Entra:

- Columna `submitted_at timestamptz` en `listings`, migración **V12**, con `updated_at`
  como relleno para las filas que ya estén en revisión.
- Campo en `Listing`, sellado en **toda** entrada a `PENDING_REVIEW` y no solo al
  enviar por primera vez: RN-062 también devuelve a la cola lo que se edita, y una
  publicación que vuelve con el sello viejo se quedaría para siempre a la cabeza.
  La regla vive en un único sitio, `selloDeRevision`.
- Índice por `(status, submitted_at)`, que es exactamente como consulta la bandeja.

**La bandeja no anota en la bitácora.** Listar no es decidir. Lo que se anota es aprobar
y rechazar, y eso ya lo hace `ModerationLog`. Anotar también el listado convertiría el
rastro en un registro de navegación, y un rastro que crece con cada refresco de pantalla
es uno que nadie lee.

**El guard de ruta ya existe.** `core/session/role.guard.ts` lo trajo HU-006 y se
reutiliza tal cual, con el renderizado en servidor y la denegación en el servidor que
ADR-0021 recoge. No se escribe uno nuevo.

Rutas nuevas en español, con carga diferida, como el resto:
`/moderacion/publicaciones` y `/moderacion/publicaciones/:id`. No se enlazan desde la
cabecera pública.

Funcionalidad nueva `features/listing-review`, con sus cuatro capas. **No cuelga de
`features/listing`**: comparten los tipos del dominio pero no el mecanismo, y una
funcionalidad no importa de otra. Lo compartido sube a `shared` o `core`, y aquí hay un
caso claro: los tipos de estado, condición y motivo de rechazo, y los ayudantes puros
que hoy viven en `features/listing/domain/listing.ts`.

Claves de Transloco nuevas bajo `listingReview.*`, y su texto va antes a
`docs/producto/textos-web.md`, que es la fuente de la que salen.

## Pruebas requeridas

Unitarias, sin TestBed:

- `submitted_at` se sella al enviar a revisión y **no cambia** al cambiar el precio. Es
  la prueba que fija el motivo de la migración.
- El caso de uso acota el tamaño de página a 50 y rechaza los valores absurdos.
- El mapeo de la fila: `own` es verdadero solo para el propio moderador, y ningún campo
  lleva el identificador del vendedor.

De componente, comportamiento observable y consultas por rol accesible:

- Los tres estados de la bandeja: cargando, vacía y error.
- Rechazar sin motivo no se puede enviar.
- La marca de atención se anuncia con texto, no solo con color.
- La sesión llega **después** de crear el componente, como en una carga real. Es la
  trampa que ya dejó `/mi-cuenta` sin cargarse nunca y que fija `account-page.spec.ts`.

De integración en el backend:

- La bandeja devuelve solo `PENDING_REVIEW` y en orden ascendente por `submitted_at`.
- Con `FEATURE_PUBLISHING` apagada la ruta responde 404, no 403. Criterio 3.
- Sin el rol, 403; sin sesión, 401.
- RN-063 sobre la API, con dos cuentas: la propia se rechaza, la ajena se acepta.

De extremo a extremo, en `e2e-completo/` porque cruza las dos mitades. **Escritas, y tres
de las seis estuvieron en rojo** hasta el 27 de agosto de 2026: ver «El fallo que dejó
tres pruebas en rojo, y su causa».

- Un vendedor envía a revisión, un moderador aprueba y la publicación queda
  `PUBLISHED`.
- Un moderador rechaza con motivo y el vendedor ve el motivo y puede corregir y
  reenviar. Cierra el ciclo que HU-007 dejó abierto.
- Una cuenta sin el rol no llega a la bandeja: criterio 2.

En `e2e/`, sin API: la ruta responde y no filtra nada en el HTML servido.

## Lo que cambió al implementarla

Cinco cosas que la historia no había previsto y que se decidieron con el código delante.

**`own` viaja en la publicación, no solo en la fila de la cola.** La historia daba por
hecho que bastaba con la fila, y no basta: el detalle se abre también por su dirección
directa, y entonces la cola no está cargada. Sobre su propia publicación, quien modera veía
los dos botones y se enteraba al pulsar, que es exactamente lo que el criterio 12 existe
para evitar. Lo encontraron tres revisiones por separado.

**El sello se pone en toda entrada a `PENDING_REVIEW`, no solo al enviar por primera vez.**
RN-062 también devuelve a la cola lo que se edita, y con el sello viejo esa publicación se
quedaba a la cabeza para siempre.

**`sellerId` desapareció de la respuesta del detalle para quien modera.** La cola lo omite
para no ser de paso una lista de quién vende qué, y dejarlo en el detalle deshacía esa
protección con una petición por fila. Quien sí lo recibe es el dueño.

**El constructor de `Listing` pasó a ser un ensamblador con nombres.** Tenía catorce
argumentos posicionales, cuatro de ellos `Instant`, así que dos cruzados compilaban sin
protestar. Agregar `submittedAt` rompió cuatro ayudantes de prueba, y eso fue el aviso.

**La cola trae solo la toma frontal.** Cargaba las ocho de cada fila —una consulta por
publicación— para pintar una miniatura, en la pantalla que más veces se abre.

## El fallo que dejó tres pruebas en rojo, y sus causas

**Resuelto el 27 de agosto de 2026.** Eran tres defectos encadenados, dos de ellos de
HU-007 y uno de la propia prueba. Se deja escrito porque la sospecha buena era la tercera
de la lista y el diagnóstico costó más de lo que debía.

Las tres pruebas del ciclo de publicación —aprobar, rechazar y reenviar, y decidir sobre
algo que ya no está pendiente— se caían en el mismo punto: `publicarYEnviarARevision`,
justo después de pulsar «Empezar», con el aviso de `COMMON_UNEXPECTED` y sin que
apareciera nunca el formulario.

### El primer defecto: crear un borrador vacío respondía 500

`POST /api/v1/listings` con el cuerpo que manda la pantalla —`{"categoryId": ...}` y nada
más— respondía **500**. Dos capas rotas por lo mismo:

- `JdbcListingRepository.guardarProducto` desreferenciaba sin guarda `title().value()`,
  `condition().name()`, `size().system()`, `color().name()`, `price().enPesos()` y las
  cuatro medidas del envío, que en `Product` son todos `@Nullable`. Excepción de puntero
  nulo. `filaAProducto` tenía el problema simétrico en la lectura: `Condition.valueOf(null)`
  y un `getLong` sobre una columna nula que devolvía un precio de cero pesos que el propio
  `Product` rechaza.
- Y aunque no lo hubiera tenido, `V9__catalog.sql` declaró `NOT NULL` las doce columnas que
  describen el producto. El INSERT tampoco habría entrado.

Las dos cosas contradicen al **criterio 5 de HU-007**, que dice que un borrador incompleto
se guarda sin exigir que esté completo. `V13__draft_product_columns_nullable.sql` quita esos
`NOT NULL`; lo obligatorio lo sigue exigiendo el dominio al enviar a revisión, que es donde
puede distinguir un borrador a medias de una publicación que se quiere publicar sin
terminar.

**Por qué no lo vio ninguna prueba:** `ListingJourneyTest` y `CatalogPersistenceTest` creaban
siempre el producto **completo**, y las de componente simulan HTTP. Nadie había creado un
borrador vacío contra PostgreSQL, que es la primera petición de la pantalla y por donde
empieza toda publicación. Hoy lo cubren dos pruebas de persistencia y una de recorrido.

### El segundo defecto: lo escrito se borraba al subir una foto

Con el 500 resuelto, las tres seguían cayendo, ahora al enviar a revisión: el servidor
respondía `CATALOG_LISTING_INCOMPLETE` y el formulario estaba **en blanco**.

`PublishPage.volcarEnElFormulario` sincroniza el formulario con lo que llega del servidor,
y corre cada vez que cambia la publicación. Subir una toma **cambia la publicación**: la
respuesta trae el producto entero. Volcándolo de golpe, lo tecleado desde el último
guardado automático se borraba, y el `markAsPristine` de después cancelaba el guardado que
iba a salvarlo. Quien escribiera el título y arrastrara una foto antes de que saltara el
guardado —1,5 s— perdía lo escrito, en silencio y sin poder recuperarlo. Es exactamente lo
contrario de lo que la pantalla promete: «puedes salir a la mitad y volver cuando quieras:
guardamos lo que llevas».

Ahora la sincronización va campo por campo y no pisa un control sucio cuyo valor difiera
del que devuelve el servidor. Marcar limpio solo lo que de verdad se guardó importa además
para el criterio 28: `camposTocados` es lo que decide si un cambio vuelve a moderación.

### Lo que la propia prueba tenía mal

Tres cosas, todas de no haberse ejecutado nunca entera:

- **No llenaba color, las medidas del grupo ni el envío**, que el criterio 6 exige para
  enviar. Con lo demás arreglado, el servidor seguía teniendo razón al rechazar.
- **No esperaba a que el guardado aterrizara** antes de subir las tomas. Un guardado y una
  subida en vuelo a la vez escriben sobre la misma publicación y el bloqueo optimista del
  criterio 34 tumba a uno de los dos; cuando el que caía era el guardado, el envío fallaba
  después por datos que sí se habían escrito.
- **Esperaba un cartel que no existe.** `listing.submit.sent` —«Enviada a revisión»— está
  en `es.json` y en `en.json` y **ninguna plantilla lo usa**. La confirmación real de que
  la publicación entró a revisión es que la acción de enviar deja su sitio a la de retirar,
  que es lo que se comprueba ahora. La clave sin usar sigue ahí: darle una región viva a la
  pantalla del vendedor es una decisión de diseño, no parte de este arreglo.

### Lo que enmascaraba la causa

Dos cosas hicieron que el diagnóstico apuntara a otro lado:

- **Una carrera en la propia prueba.** `ingresar()` hacía clic en «Entrar» y volvía sin
  esperar; `dejarUnaVendedoraVerificada` navegaba acto seguido a `/publicar` y abortaba el
  `POST /auth/login` en vuelo —en la traza sale con estado `-1`—. Sin sesión, el 500 de
  arriba no llegaba a producirse y lo que se veía era un 401 con el mensaje genérico. Por
  eso el síntoma cambiaba entre el primer intento y el reintento.
- **El registro del backend nunca llegó al artefacto.** `upload-artifact@v4` ignora los
  archivos ocultos salvo que se le diga lo contrario, y `.registro-backend.log` empieza por
  punto. De ahí la afirmación —equivocada— de que «el backend no registra ningún error»: el
  registro que se miró no era el de la corrida que falló. Corregido con
  `include-hidden-files: true`.

La primera sospecha de la lista, `reuseExistingServer`, no tenía nada que ver: en
integración continua está en `false` y el fallo se daba igual.

## Documentación que trae esta historia

- La sección `listingReview.*` de `docs/producto/textos-web.md`.
- La columna `submitted_at` en `docs/arquitectura/modelo-datos.md`.
- El endpoint de la bandeja en `docs/arquitectura/contrato-api.md`.
- El punto «panel de moderación» de `docs/producto/alcance.md`, que con esta historia
  queda completo por fin: HU-006 trajo la mitad de las verificaciones y esta la de las
  publicaciones.
