# HU-006 — Bandeja del moderador

**Fase:** 2 | **Estado:** hecha
**Reglas que aplica:** RN-010, RN-012, RN-013, RN-014, RN-046, RN-059, RN-060

## Objetivo

Una persona de Sendik con rol de moderador puede ver las solicitudes de
verificación que esperan revisión, mirar el documento y la selfie, y aprobar o
rechazar con motivo, desde la interfaz y sin tocar la base de datos.

## Por qué ahora

HU-002 está terminada y **apagada**. Los cinco endpoints de revisión existen y
están probados, pero sin pantalla el sello solo se otorga con una llamada directa
a la API y el rol de moderador solo se concede con un `INSERT` a mano. Mientras
eso siga así, `FEATURE_SELLER_VERIFICATION` no se puede encender: se le pediría a
alguien su cédula sin que exista forma de responderle.

Esta historia es lo que convierte HU-002 en algo que se puede poner en producción.

## Alcance

Entra:

- Bandeja de solicitudes en `PENDING_REVIEW`, la más vieja primero.
- Detalle de una solicitud con sus datos y sus tres imágenes.
- Aprobar.
- Rechazar con motivo de la lista cerrada y nota opcional.
- Protección de la ruta por rol.

No entra:

- **Revocar el sello** (`POST /{id}/revocation`). El endpoint existe, pero actúa
  sobre una verificación ya `VERIFIED` y hoy no hay forma de encontrar ese
  identificador desde la interfaz: la bandeja solo devuelve lo pendiente.
  Ofrecerlo exigiría un buscador de vendedores verificados, es decir un endpoint
  nuevo, y eso no es «falta la interfaz». Se decide aparte, con su historia.
- Moderación de publicaciones. Es el otro sentido del punto «panel de moderación»
  de la Fase 2 y llega con la publicación de prenda.
- Panel administrativo, reportes y métricas: Fase 4.
- Conceder el rol de moderador desde la interfaz. Sigue siendo un `INSERT`
  manual, documentado en HU-002, hasta que llegue el panel administrativo.
- Paginación. La bandeja pide un límite y el backend lo acota a 50. Con el
  volumen de la primera fase eso sobra; el día que no sobre, es un cambio de
  contrato y se decide entonces.

## Criterios de aceptación

1. Dado un usuario con el rol `MODERATOR`, cuando abre la ruta de la bandeja,
   entonces ve las solicitudes en `PENDING_REVIEW` ordenadas de la que lleva más
   tiempo esperando a la más reciente.

2. Dado un usuario **sin** el rol `MODERATOR` —con sesión o sin ella—, cuando
   intenta abrir la ruta, entonces no ve ningún dato de ninguna solicitud y se le
   lleva a una pantalla que no revela que la bandeja exista.

3. Dado que la bandeja está vacía, cuando el moderador la abre, entonces ve el
   estado vacío del sistema y no una tabla sin filas.

4. Dado que la bandeja está cargando, cuando todavía no hay respuesta, entonces
   se muestra el esqueleto de `marca.css`; y si la petición falla, un estado de
   error con la acción de reintentar.

5. Dado el detalle de una solicitud, cuando el moderador lo abre, entonces ve el
   tipo de documento, **los cuatro últimos dígitos** del documento y de la
   cuenta, el nombre del titular de cada uno, el banco, el tipo de cuenta, el
   número de intento y desde cuándo espera. Nunca un número completo, tampoco
   para él (criterio 11 de HU-002, RN-046).

6. Dado el detalle de una solicitud, cuando el moderador pide una de las tres
   imágenes, entonces se muestra la imagen y **queda registrada la lectura** en
   la bitácora con su identidad y un motivo. La interfaz manda siempre un motivo;
   el moderador no lo escribe.

7. Dado que el nombre del titular de la cuenta y el del documento no coinciden,
   cuando el moderador abre el detalle, entonces la discrepancia se señala de
   forma visible y no solo por color (RN-012).

8. Dado el detalle de una solicitud, cuando el moderador aprueba, entonces la
   solicitud pasa a `VERIFIED`, desaparece de la bandeja y él vuelve a la lista
   con la confirmación de lo que hizo.

9. Dado el detalle de una solicitud, cuando el moderador rechaza, entonces debe
   elegir un motivo de la lista cerrada; la nota es opcional y no puede pasar de
   500 caracteres. Sin motivo elegido, la acción no se puede enviar.

10. Dado que el moderador ha elegido aprobar o rechazar, cuando confirma,
    entonces la decisión se le pide confirmar una vez: son actos que notifican
    por correo a una persona y no se deshacen desde la interfaz.

11. Dado que una solicitud ya fue resuelta por otro moderador, cuando este
    intenta decidir sobre ella, entonces se le dice que ya no está pendiente y la
    bandeja se refresca. No se muestra un error genérico.

12. Dado un moderador que tiene su propia solicitud de verificación en revisión,
    cuando intenta aprobarla o rechazarla, entonces se rechaza la operación
    **en el backend** y la interfaz no ofrece la acción (RN-060).

13. Dado cualquier estado de esta pantalla, cuando se inspecciona lo que llega al
    navegador, entonces no aparece el número de documento completo, el número de
    cuenta completo, ni ninguna dirección de las imágenes: solo los bytes que
    devuelve el endpoint autorizado.

14. Dado el modo oscuro y una ventana de 360px, cuando se recorre la bandeja y el
    detalle, entonces todo es legible, operable con teclado y sin desbordamiento
    horizontal.

## Casos borde

- **Dos moderadores sobre la misma solicitud.** El segundo llega tarde: criterio
  11. La carrera la resuelve el backend con las transiciones de RN-059, no la
  interfaz.
- **La imagen no está.** Una solicitud puede llegar a revisión con el documento y
  la selfie entregados, pero el archivo puede faltar por un fallo de despliegue.
  Se dice, y la solicitud sigue siendo decidible: rechazar por fotos ilegibles es
  una respuesta válida.
- **La sesión caduca con el detalle abierto.** El token de acceso vive 15
  minutos y revisar una solicitud puede llevar más. La renovación es la del resto
  del sitio; lo que no puede pasar es perder una nota ya escrita.
- **Tercer intento.** RN-014 permite tres; el cuarto exige revisión manual. La
  bandeja muestra el número de intento para que quien revisa lo sepa antes de
  rechazar.
- **Nota con datos de un tercero.** La nota viaja a la persona rechazada. La regla
  —nunca información judicial ni de terceros— la impone quien escribe, no una
  validación; la pantalla lo recuerda donde se escribe.
- **Volver atrás con el navegador** después de decidir. No debe reenviar la
  decisión.
- **Recargar el detalle** con la dirección directa: tiene que funcionar, incluido
  el rol, sin pasar por la lista.

## Diseño

Pantalla interna, no pública: no lleva el acento bronce, que está reservado a la
acción principal de las pantallas de cara al comprador. Las dos acciones son
aprobar y rechazar y se distinguen por forma y texto, no por color.

- Lista: una fila por solicitud con el nombre del titular, el tiempo esperando y
  el número de intento. Destinos táctiles de 44px.
- Detalle: los datos arriba, las tres imágenes debajo. Las imágenes se piden **al
  abrirlas**, no al cargar la pantalla: cada una es un acceso a un dato sensible y
  se registra, así que abrir la ficha no puede registrar tres lecturas que nadie
  hizo.
- Estados de carga, vacío y error con las clases del sistema (`.esqueleto`,
  `.vacio`).
- En móvil la lista es una pila de tarjetas, no una tabla con desplazamiento
  horizontal.
- La discrepancia de titular del criterio 7 se marca con etiqueta de texto además
  del color.

## Notas técnicas

Los cinco endpoints ya existen. Esta historia **no crea ninguno**, salvo lo que
exija RN-060.

| Método y ruta | Uso en esta historia |
|---|---|
| `GET /api/v1/verifications?limite=20` | La bandeja. El backend acota el límite a 50 |
| `GET /api/v1/verifications/{id}/images/{imagen}?motivo=...` | `imagen` es `document-front`, `document-back` o `selfie`. Devuelve bytes con `Cache-Control: no-store` |
| `POST /api/v1/verifications/{id}/approval` | Sin cuerpo |
| `POST /api/v1/verifications/{id}/rejection` | `{ reason, note? }`, `note` hasta 500 |
| `POST /api/v1/verifications/{id}/revocation` | **No se usa aquí.** Fuera de alcance |

Todos exigen `hasRole('MODERATOR')` por partida doble —`SecurityConfig` y
`@PreAuthorize`— y solo existen con `FEATURE_SELLER_VERIFICATION` encendida.

**Cambio de backend que sí trae esta historia:** RN-060. Un moderador no puede
decidir sobre su propia solicitud. Se comprueba en el caso de uso, no en el
controlador ni en la interfaz, y tiene su código de error propio.

**El primer guard de ruta del proyecto.** Hoy no hay ninguno: `/mi-cuenta` deja
que el componente resuelva la sesión. Aquí hace falta uno por rol, y lo delicado
es el SSR: la sesión llega por la cookie de refresco **después** de que el
componente nace, así que un guard que lea el rol de inmediato rechazaría siempre
a quien recarga la página. Tiene que esperar a que la sesión se resuelva antes de
decidir. El guard **no es la cerradura** —esa es del backend, que responde 403
igual— sino lo que evita enseñar una pantalla que no se va a poder llenar.

Ruta nueva en español, con carga diferida, como el resto:
`/moderacion/verificaciones` y `/moderacion/verificaciones/:id`. No se enlaza
desde la cabecera pública; el moderador la conoce.

Funcionalidad nueva `features/verification-review`, con sus cuatro capas. **No
cuelga de `features/seller-verification`**: comparten los tipos del dominio pero
no el mecanismo, y una funcionalidad no importa de otra. Lo compartido sube a
`shared` o `core`.

Claves de Transloco nuevas bajo `verificationReview.*`, y su texto va antes a
`docs/producto/textos-web.md`, que es la fuente de la que salen.

El motivo del criterio 6 lo manda la interfaz con un valor fijo. Es una cadena
que va a la bitácora y no la ve nadie más: no es texto de pantalla y no lleva
clave de Transloco.

## Pruebas requeridas

Unitarias, sin TestBed:

- La regla de RN-060 en el dominio o el caso de uso: mismo actor y dueño, se
  rechaza.
- El mapeo de la respuesta de la bandeja: ningún campo lleva un número completo.

De componente, comportamiento observable y consultas por rol accesible:

- Los tres estados de la bandeja: cargando, vacía y error.
- Rechazar sin motivo no se puede enviar.
- La discrepancia de titular se anuncia con texto, no solo con color.
- **Las imágenes no se piden al abrir el detalle**, solo al abrir cada una. Es la
  prueba que impide que una ficha registre tres lecturas que nadie hizo.
- La sesión llega **después** de crear el componente, como en una carga real. Es
  la trampa que ya dejó `/mi-cuenta` sin cargarse nunca y que fija
  `account-page.spec.ts`.

De integración en el backend:

- RN-060 sobre la API, con dos cuentas: la propia se rechaza, la ajena se acepta.
- El acceso a una imagen deja fila en `verification_access_log` con actor, acción
  y motivo.

De extremo a extremo, en `e2e-completo/` porque cruza las dos mitades:

- Un moderador aprueba una solicitud y la persona queda con el sello y el rol.
- Un moderador rechaza con motivo y la persona puede corregir y reenviar.
- Una cuenta sin el rol no llega a la bandeja: criterio 2.
- Criterio 13 sobre lo que llega al navegador.

En `e2e/`, sin API: la ruta responde y no filtra nada en el HTML servido.

## Lo que cambió al implementarla

Tres cosas que la historia no había previsto y que se decidieron con el código delante.

**El rol de moderador se concede también al registrarse**, no solo al arrancar. La
historia daba por hecho que `SECURITY_BOOTSTRAP_MODERATORS` bastaba, y no basta: solo
alcanza a cuentas que ya existen, así que no sirve ni para las pruebas —que crean las
suyas por la interfaz— ni para el orden natural de dar de alta a alguien, que es decidir
quién modera y después esa persona registrarse.

**La bandeja dice si la solicitud es tuya** (`own`). Sin ese campo, la mitad de interfaz
del criterio 12 no se podía cumplir: el resto del DTO no dice de quién es cada solicitud,
a propósito (criterio 11 de HU-002), así que la pantalla no tenía cómo saberlo y quien
revisa se enteraba después de pulsar. Se manda un booleano y no el identificador del
dueño: responde lo único que la pantalla necesita sin decir quién es nadie.

**Las rutas se renderizan en servidor.** El plan era `RenderMode.Client`, que es lo
natural para una pantalla interna, y no funciona: `APP_CONFIG` llega por el estado
transferido del SSR y sin él la aplicación no arranca. En su lugar el guard **deniega en
el servidor**, que cumple el criterio 2 igual de bien —lo servido es la página de «no
existe»— y además evita colgar el SSR esperando una sesión que allí no llega nunca.
ADR-0021 lo recoge.

Y una cosa que **no** se hizo: RN-060 no se prueba en `e2e-completo/`. Exigiría dejar a
la cuenta que modera con una solicitud propia enviada, un estado que sobrevive a la
corrida y hace que la siguiente empiece donde la anterior la dejó. Queda cubierta en el
caso de uso con sus dos caras, en `SellerVerificationJourneyTest` contra la base de
verdad, en el controlador con su 403 y su código propio, y en la pantalla que no ofrece
la acción sobre lo propio.

## Documentación que trae esta historia

Aplicado al escribirla:

- **RN-060** en `docs/producto/reglas-negocio.md`, sección «Vendedor». Cierra la
  decisión que HU-002 dejó anotada y que hoy no comprueba nadie.
- **Textos** en `docs/producto/textos-web.md`, sección «Bandeja del moderador».
  Incluye los cinco motivos de rechazo **en su versión para quien revisa**, que
  no es la de `sellerVerification.reasons.*`: aquellos están escritos en segunda
  persona para quien recibe el rechazo, y el moderador necesita etiquetas cortas
  de una lista.
- **`docs/producto/alcance.md`**: el punto «panel de moderación» de la Fase 2
  queda marcado como cubierto a medias.

Sin cambios, y por qué:

- **`docs/arquitectura/modelo-datos.md`**: esta historia no crea tablas ni
  columnas. `verification_access_log` ya tiene lo que hace falta, y
  `VIEW_BANK_ACCOUNT` sigue sin usarse por lo que dice HU-002.
- **`docs/arquitectura/contrato-api.md`**: no hay dónde escribirlo. El archivo
  fija las convenciones y solo tabula los códigos `FILE_`; el catálogo de códigos
  vive «en un enum del backend y en el archivo de traducciones del frontend», y
  exige que los dos cambien **en el mismo commit**. Escribir ahí un código que
  todavía no existe en ninguno de los dos sitios sería documentar algo que no
  está. El código queda decidido aquí y entra con la implementación:

  | Código | Estado | Texto en `errors.byCode.*` |
  |---|---|---|
  | `SELLER_SELF_REVIEW_FORBIDDEN` | 403 | No puedes decidir sobre tu propia solicitud. La revisa otra persona. |

Queda abierto, y necesita decisión de producto:

- **«Bandeja» en el glosario.** No se agrega por ahora porque el glosario es
  bilingüe y pide un nombre de código, y ese nombre no se inventa aquí
  (`CLAUDE.md`). La entrada «Moderador» ya cubre el rol y no necesita cambio. Lo
  que conviene decidir antes de la segunda es que van a existir tres bandejas
  distintas —verificaciones, publicaciones y disputas de la Fase 4— y llamarlas
  igual sin distinguirlas es lo que hace que después nadie sepa de cuál se habla.

## Lo que se corrigió después, el 3 de septiembre de 2026

**La bandeja no se podía pasar de la primera página.** Se destapó al arreglar la
repetibilidad de `e2e-completo`, y no es un defecto de aquella suite sino de aquí.

`SellerVerificationRepository.pendientesDeRevision` recibía un `limite` y nada más:
`LIMIT` sin `OFFSET`. `ListPendingVerificationsUseCase` decía en su javadoc que «la
pantalla pagina pidiendo otra vez», y era falso —pedir otra vez devolvía exactamente
lo mismo—. El puerto, por su parte, defendía la ausencia de paginación con un
argumento que describe bien la carga y mal el alcance: la cola ordena lo más viejo
primero y decidir saca la fila, así que **drena sola** y quien revisa siempre tiene
trabajo en la primera página. Lo que ese argumento no cubre es *buscar*: un moderador
que quiere llegar a una solicitud concreta —porque le escribieron, porque la
reclamaron— no podía pasar de las primeras veinte por ningún camino, y nada en la
pantalla decía que hubiera más.

Lo que cambió:

- La consulta lleva `OFFSET`, y **desempata por `id`**. `updated_at` sola no es un
  orden total: dos solicitudes enviadas en el mismo instante pueden salir en cualquier
  orden, y si ese orden cambia entre una página y la siguiente hay filas que aparecen
  dos veces y filas que no aparecen nunca. Mientras esto fue un tope sin salto, el
  defecto no se podía observar.
- El endpoint se alineó al contrato: `?page=&size=` en vez de `?limite=` —el único
  parámetro de la API que estaba en español— y respuesta `{ items, page, size }` en vez
  de una lista pelada, que es la forma que ya usaba la cola de publicaciones. El tamaño
  va acotado a 50 y por encima responde 400, no recorta en silencio.
- La pantalla tiene «Anterior» y «Siguiente», con el número de página anunciado como
  región viva: al pulsar, lo único que cambia es la lista.
- **Los botones se marcan con `aria-disabled` y no con `disabled`.** Es la lección que
  HU-011 ya pagó: un botón que se deshabilita en el mismo tick del clic con el foco
  dentro manda el foco a `body`, y quien navega con teclado tiene que tabular desde el
  principio del documento. Aquí pasaría justo al llegar al extremo.
- Y por lo mismo, el estado «no disponible» **no** reutiliza la paleta de
  `.btn:disabled`. Aquella pinta blanco sobre `--color-deshabilitado`, que en modo claro
  da 1.96:1: un control de verdad deshabilitado está exento del mínimo de contraste,
  pero éstos siguen habilitados a propósito y para WCAG siguen siendo componentes
  activos. Se baja el énfasis con `--color-texto-suave` (4.68:1 en claro, 7.33:1 en
  oscuro) y `--color-borde-control` (3.36:1 y 5.29:1).

Y lo que no tenía guardián: `pendientesDeRevision` **no tenía ninguna prueba de
persistencia**. Se podía borrar el desplazamiento entero —y de hecho no existía— sin
que nada se pusiera en rojo. Ahora hay dos, y una de ellas envía tres solicitudes en el
mismo instante, que es el caso que el desempate existe para ordenar.

### El «Siguiente» que llevaba a una página vacía

La primera versión de la paginación dejó un defecto propio: la pantalla deducía si
había otra página de que la página viniera llena. Esa deducción no distingue una página
llena con más detrás de una página llena que es **la última**, y son el mismo dato.
Ocurre siempre que el total es múltiplo exacto del tamaño, que en una cola que se vacía
de veinte en veinte no es raro: pasa cada vez que da la vuelta. Quien revisa pulsaba
«Siguiente», no encontraba nada, y no podía saber si la cola se había acabado o si algo
se había roto.

Ahora lo contesta el servidor, en `hasMore`. Se resuelve preguntando al repositorio si
**queda alguna después de esta página**, no contando: un `COUNT` obliga a recorrerlas
todas para responder un sí o un no.

La primera versión lo resolvía distinto —pidiendo una fila de más y usando su presencia
como señal— y de ahí salieron dos lecciones que conviene no repetir.

La primera, un defecto peor en el propio puerto. Su firma era `(pagina, tamano)` y el
adaptador derivaba el desplazamiento del mismo argumento que el límite: `OFFSET = pagina
* tamano`. Pedir una fila de más movía también el arranque —`(1, 21)` saltaba 21 filas
en vez de 20— y **la solicitud número 21 no salía en ninguna página**, sin que nada lo
dijera. Es exactamente lo que la paginación vino a evitar, y en silencio. El puerto pasó
a recibir `(salto, cuantas)`, que además es lo que impide que la trampa se pueda volver
a escribir.

La segunda, de datos personales. `pendientesDeRevision` devuelve el agregado entero, así
que traer una fila de más **descifraba la cédula y la cuenta bancaria de una persona con
el único propósito de comprobar que su fila existía**, y las tiraba. No era una fuga
—nada de eso salía de la capa de aplicación— pero es procesar un dato sensible sin
finalidad, que es lo que la minimización de la Ley 1581 desaconseja. Por eso la pregunta
tiene ahora su propio método, `hayPendientesDesde`, que resuelve con un `EXISTS` sobre el
índice parcial y no lee ninguna columna de la fila.

Ninguna de las pruebas que había podía verlo —la del recorrido va de una en una, y con
tamaño 1 el producto coincide con el salto—, así que hay una nueva que pide dos ventanas
del mismo sitio con límites distintos.

## Lo que sigue abierto

**La cola de publicaciones tiene la mitad del mismo problema.** Su backend pagina bien
y `ListingReviewApi.pendientes(pagina, tamano)` acepta página, pero
`ListingReviewStore.queue` la llama sin argumentos —siempre la página 0— y
`queue-page.html` no tiene ningún control para avanzar: la paginación existe entera y
nadie la usa. No entró aquí porque esta historia es la bandeja de verificaciones. Es
corto y ya está todo lo difícil hecho.

Lo que sí se hizo ya, para que esa cola no repita lo de aquí: `ListingRepository`
recibe `(salto, cuantas)` como esta, y su consulta **desempata por `id`**. Le faltaba,
y no era teórico —la retrollenada de V12 le puso a todas las que ya esperaban turno el
valor de `updated_at`, y ahí los empates son fáciles—: con el instante repetido, dos
páginas contiguas traían la misma fila dos veces y se dejaban otra sin traer.
Comprobado quitando el desempate y viendo la prueba en rojo.

Falta solo la mitad de arriba: `ListingReviewStore.queue` sigue pidiendo la página 0 y
la pantalla no tiene controles. Cuando los tenga, querrá su `hasMore` como esta.
