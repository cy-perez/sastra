# HU-007 — Publicación de producto

**Fase:** 2 | **Estado:** pendiente
**Reglas que aplica:** RN-011, RN-013, RN-015 a RN-025, RN-029, RN-030,
RN-061, RN-062, RN-063

## Objetivo

Un vendedor verificado describe su producto, sube sus tomas y lo envía a revisión;
un moderador la aprueba o la rechaza con motivo, y solo entonces la publicación es
visible.

Cubre las dos familias que Sendik admite: moda, nueva y de segunda, y tecnología,
solo nueva (RN-064).

## Por qué ahora

Es el tronco de la Fase 2. Hoy no existe ni una tabla de catálogo: la última
migración es `V8__seller_verification.sql`. HU-003 no se puede implementar sola
porque sus criterios 5 a 10 suben una secuencia que no tiene dónde aterrizar y
sus criterios 11 a 19 pintan un visor dentro de una ficha que no existe. La
moderación de publicaciones —RN-015, la mitad pendiente del punto «panel de
moderación» de `alcance.md`— tampoco tiene nada que moderar hasta que esto esté.

## Decisiones tomadas el 24 de agosto de 2026

Siete cosas que esta historia daba por resueltas y no lo estaban. Se decidieron
antes de escribir los criterios, porque cinco de ellas deciden el esquema.

- **Las medidas y la talla se desacoplan del árbol de categorías.** El árbol
  sigue sin definir y es una decisión aplazada de `alcance.md`. Lo que esta
  historia fija es el contrato: cada categoría declara un **grupo de medida** y
  un **sistema de talla**, los dos de lista cerrada. Qué categorías existen y
  cuál declara cada una es prerrequisito de implementación, no de esta historia.
- **Editar el contenido de una publicación viva la devuelve a revisión; cambiar
  el precio no.** RN-015 dice que todo pasa por moderación antes de ser visible y
  RN-030 da por hecho que el precio cambia en una publicación viva. Es la única
  lectura que satisface las dos: se modera lo que describe el producto, no lo que
  cuesta.
- **`PAUSED` es del vendedor; `ARCHIVED` es de los dos.** El vendedor pausa,
  reanuda y archiva; el moderador puede archivar una publicación ya visible que
  infringe RN-024. Sin eso, aprobar es irreversible y la única salida a una
  réplica detectada tarde sería la base de datos.
- **El contrato de imágenes vive aquí; el asistente de captura vive en HU-003.**
  Esta historia define subida, validación y la regla de las ocho tomas. Con eso
  la publicación se puede probar de punta a punta cargando desde galería, sin
  esperar al asistente.
- **La subida es por el backend, no por URL firmada.** Lo decidió ADR-0018, que
  es posterior a HU-003; la nota técnica de HU-003 decía «subida directa al
  almacenamiento con URL firmada» y ya está corregida. La URL firmada se
  reconsidera solo si HU-003 mide que ocho tomas por el backend salen lentas o
  caras, que es la condición de revisión que el propio ADR-0018 anota.
- **El rango de precio de RN-020 es blando.** Se puede guardar un precio fuera de
  10.000 a 20.000.000 y la publicación queda marcada para revisión más atenta.
  RN-019, cuando quiere un bloqueo duro, dice «el formulario no deja continuar»;
  RN-020 no lo dice, dice «exige revisión manual». La marca es la misma que ya
  pide el criterio 8 de HU-003 para las tomas cargadas desde galería.
- **El motivo de rechazo es lista cerrada más nota**, como en HU-002. Texto libre
  solo no se traduce, no se mide y no le dice al vendedor qué corregir, que es
  exactamente lo que RN-022 exige.

## Alcance

Entra: borrador de publicación, datos del producto, subida y validación de las
ocho tomas, envío a revisión, decisión del moderador con motivo, edición, pausa,
reanudación y archivo, y el listado propio con el que el vendedor llega a sus
borradores.

No entra: el asistente de captura y el visor 360 (HU-003), la bandeja del
moderador de publicaciones, el catálogo público con su rejilla y sus filtros, la
ficha de producto, los favoritos, el panel del vendedor completo y la búsqueda.

**La bandeja de moderación de publicaciones se separa a propósito**, con el mismo
corte que se usó entre HU-002 y HU-006: aquí quedan los endpoints de decisión,
probados; la pantalla con la que se usan es otra historia. Sin ese corte, esta
historia son dos.

## Criterios de aceptación — quién publica

1. Dado un usuario autenticado sin el sello de vendedor verificado, cuando pide
   crear un borrador, entonces recibe 403 `CATALOG_SELLER_NOT_VERIFIED` y no se
   crea ninguna publicación (RN-011).
2. Dado un vendedor cuya verificación quedó en `REVOKED`, cuando pide crear un
   borrador, entonces recibe 403; y sus publicaciones que ya estaban en
   `PUBLISHED` siguen visibles sin cambio alguno (RN-013).
3. Dado `FEATURE_PUBLISHING` apagada, cuando alguien llama a cualquier endpoint
   de esta historia, entonces recibe 404 y el frontend no muestra la entrada de
   publicar en ningún menú ni ruta.

## Criterios de aceptación — borrador y datos

4. Dado un vendedor verificado, cuando crea un borrador, entonces la publicación
   queda en `DRAFT`, asociada a él, con existencia 1 (RN-025), y la respuesta es
   201 con cabecera `Location`.
5. Dado un borrador incompleto, cuando el vendedor guarda lo que lleva, entonces
   se guarda sin exigir que esté completo y la publicación sigue en `DRAFT`.
   Salir a la mitad y volver retoma donde iba.
6. Dado un borrador al que le falta alguno de los datos obligatorios —título,
   descripción, categoría, condición, talla, las medidas del grupo que declara la
   categoría, color, precio, peso y las tres dimensiones de envío—, cuando lo
   envía a revisión, entonces recibe 422 con una entrada en `errors` por cada
   campo que falta y la publicación sigue en `DRAFT`.
7. Dado un borrador sin marca, cuando lo envía a revisión, entonces se acepta: la
   marca es opcional. Se guarda como texto libre con los espacios colapsados y
   recortada al límite de longitud.
8. Dado un color que no pertenece a la lista cerrada, cuando se envía, entonces
   la respuesta es 400 con `VALIDATION_*` sobre el campo `color`.
9. Dada una condición que no es una de las cuatro del glosario, cuando se envía,
   entonces la respuesta es 400. No hay una quinta y el backend no la acepta.
   Cuáles de las cuatro admite la publicación lo decide su categoría: ver los
   criterios de tecnología.
10. Dado el grupo de medida que declara la categoría elegida, cuando falta una de
    las medidas obligatorias de ese grupo, o no es un número positivo en
    centímetros, entonces la respuesta es 422 y no se puede enviar a revisión
    (RN-021).
11. Dado un precio entre 10.000 y 20.000.000, cuando se guarda, entonces se
    acepta como entero de pesos sin decimales, en el objeto de dinero del
    contrato: `{"amount": 185000, "currency": "COP"}`.
12. Dado un precio fuera de ese rango, cuando el vendedor envía a revisión,
    entonces se acepta y la publicación queda con `requiresAttention` y el motivo
    `PRICE_OUT_OF_RANGE`, que el moderador ve destacado (RN-020).
13. Dado un precio con decimales, negativo o cero, cuando se envía, entonces la
    respuesta es 400 y nunca se guarda. El dinero se calcula con decimales
    exactos, jamás con punto flotante (RN-029).

## Criterios de aceptación — tomas

14. Dado un vendedor con su borrador, cuando sube una toma indicando su posición
    de 0 a 7, entonces el backend decide el tipo real por los bytes de cabecera,
    quita el EXIF, comprueba la proporción 3:4 y el mínimo de 900 x 1200 px,
    guarda la imagen en el almacén público con clave opaca y responde 201
    (ADR-0018, RN-018, RN-019).
15. Dado un archivo que no es una imagen aceptada, entonces 415
    `FILE_TYPE_UNSUPPORTED`; dado uno que pasa del tope de tamaño, 413
    `FILE_TOO_LARGE`; dada una imagen válida por debajo del mínimo de píxeles,
    422 `FILE_DIMENSIONS_TOO_SMALL`. La extensión y el `Content-Type` que declara
    el cliente no deciden nada.
16. Dada una toma ya guardada en una posición, cuando el vendedor sube otra en la
    misma posición, entonces la nueva reemplaza a la anterior y la anterior se
    borra del almacén. Nunca hay dos tomas en la misma posición.
17. Dado un borrador con menos de ocho tomas, o al que le falta alguna de las
    cuatro canónicas —las de 0, 90, 180 y 270 grados—, cuando se envía a
    revisión, entonces la respuesta es 422 `CATALOG_SHOTS_INCOMPLETE` y sigue en
    `DRAFT` (RN-016, RN-017).
18. Dada una toma cargada desde galería en vez de capturada, cuando se confirma,
    entonces la publicación queda marcada para revisión más atenta, igual que en
    el criterio 8 de HU-003. El backend no puede distinguirlo por sí mismo: lo
    declara el cliente, y por eso solo suma una marca y nunca quita una
    validación.

## Criterios de aceptación — revisión

19. Dado un borrador con todo completo, cuando el vendedor lo envía a revisión,
    entonces pasa a `PENDING_REVIEW`, deja de ser editable y la interfaz le
    informa el plazo de respuesta configurado.
20. Dada una publicación en `PENDING_REVIEW`, cuando el vendedor la retira,
    entonces vuelve a `DRAFT`. Si el moderador ya decidió, la respuesta es 409
    `CATALOG_LISTING_INVALID_STATE` y la decisión del moderador se mantiene.
21. Dado un moderador, cuando aprueba, entonces la publicación pasa a `PUBLISHED`
    con su fecha de publicación, se vuelve visible y queda registrado el evento
    de moderación con actor, acción y fecha (RN-015).
22. Dado un moderador, cuando rechaza, entonces debe elegir un motivo de la lista
    cerrada y puede añadir una nota; la publicación pasa a `REJECTED` y el
    vendedor ve el motivo y la nota (RN-022).
23. Dada una publicación rechazada, cuando el vendedor la retoma, entonces vuelve
    a `DRAFT`, conserva todos sus datos y sus tomas, y puede corregir y reenviar
    sin límite de intentos (RN-022).
24. Dado un moderador que es el vendedor de esa misma publicación, cuando intenta
    aprobarla o rechazarla, entonces la respuesta es 403
    `CATALOG_SELF_MODERATION_FORBIDDEN` (RN-063). La comprobación es del
    servidor: esconder el botón no es la regla.
25. Dado un usuario sin rol de moderador, cuando llama a un endpoint de decisión,
    entonces la respuesta es 403 aunque sea el dueño de la publicación.
26. Dada cualquier decisión del moderador, cuando se registra, entonces se
    notifica por correo al vendedor, con el motivo cuando es un rechazo.

## Criterios de aceptación — después de publicada

27. Dada una publicación en `PUBLISHED`, cuando el vendedor cambia título,
    descripción, marca, categoría, condición, talla, medidas, color o cualquier
    toma, entonces vuelve a `PENDING_REVIEW` y deja de ser visible hasta que se
    apruebe otra vez (RN-015, RN-062).
28. Dada una publicación en `PUBLISHED`, cuando el vendedor cambia únicamente el
    precio, el peso o las dimensiones de envío, entonces sigue en `PUBLISHED` y
    visible, sin pasar por moderación (RN-030, RN-062).
29. Dada una publicación en `PUBLISHED`, cuando el vendedor la pausa, entonces
    pasa a `PAUSED` y deja de ser visible; cuando la reanuda, vuelve a
    `PUBLISHED` sin pasar por moderación.
30. Dada una publicación en `PUBLISHED` o en `PAUSED`, cuando el vendedor la
    archiva, entonces pasa a `ARCHIVED` y no vuelve a ningún otro estado.
31. Dado un moderador, cuando archiva una publicación ya visible por infringir
    RN-024, entonces pasa a `ARCHIVED`, queda el evento de moderación con motivo
    y el vendedor recibe correo.
32. Dada una publicación en `SOLD`, cuando alguien intenta editarla, reactivarla
    o archivarla, entonces la respuesta es 409. `SOLD` es terminal (RN-023).
33. Dado alguien no autenticado, cuando pide una publicación que no está en
    `PUBLISHED`, entonces recibe 404 y nunca 403: el estado de una publicación
    que no es visible no se revela.
34. Dadas dos peticiones concurrentes sobre la misma publicación, cuando las dos
    intentan cambiar su estado, entonces la segunda falla con 409 por bloqueo
    optimista sobre `version` y no se pierde ninguna decisión.

## Criterios de aceptación — tecnología

La familia de tecnología entró el 24 de agosto de 2026 y solo se vende nueva.
Estos criterios son los únicos que la distinguen de la moda; en todo lo demás una
publicación de tecnología se comporta igual.

35. Dada una categoría con `allows_used` en falso —toda la familia de
    tecnología—, cuando el vendedor declara una condición distinta de nueva,
    entonces la respuesta es 422 `CATALOG_CONDITION_NOT_ALLOWED` y no se guarda.
    La comprobación es del dominio: esconder las tres opciones en el formulario no
    es la regla, porque el endpoint se puede llamar sin pasar por él (RN-064).
36. Dada una publicación de moda, cuando el vendedor intenta declararla sellada o
    declarar meses de garantía, entonces la respuesta es 422. Los dos campos solo
    existen en tecnología.
37. Dada una publicación de tecnología declarada sellada, cuando se envía a
    revisión, entonces se le exigen exactamente cuatro tomas del vendedor, las
    cuatro canónicas del empaque, y **no** las ocho de RN-017. La ficha no ofrece
    visor giratorio (RN-065).
38. Dada una publicación de tecnología **no** declarada sellada, cuando se envía a
    revisión, entonces se le exigen las ocho tomas como a cualquier otra y no
    admite imágenes de referencia.
39. Dada una publicación que no es tecnología sellada, cuando el vendedor sube una
    imagen de referencia, entonces la respuesta es 422
    `CATALOG_REFERENCE_IMAGE_NOT_ALLOWED` (RN-066).
40. Dada una publicación de tecnología sellada sin ninguna toma propia, cuando se
    envía a revisión con solo imágenes de referencia, entonces la respuesta es 422
    `CATALOG_SHOTS_INCOMPLETE`. Una imagen de referencia nunca cuenta como toma:
    sin una foto real no hay prueba de que el producto exista.
41. Dada una ficha con imágenes de referencia, cuando se muestra, entonces cada
    una va rotulada como referencia en el carrusel y en la ficha, en los dos
    idiomas, y ninguna aparece como fotograma frontal (RN-066).
42. Dado un dispositivo con garantía del fabricante, cuando el vendedor declara
    los meses, entonces la ficha dice que responde el vendedor y no Sendik
    (RN-067). Ningún texto de esta historia llama Respaldo a esa garantía ni al
    revés.

## Casos borde

- **El moderador aprueba mientras el vendedor edita.** Lo resuelve el criterio
  34: gana quien llegue primero y el otro recibe 409 con el estado actual.
- **El vendedor cambia una publicación de una categoría de moda a una de
  tecnología** con condición «con detalles» ya declarada. El cambio se rechaza con
  el criterio 35: la categoría nueva no admite esa condición, y no se corrige la
  condición por su cuenta.
- **Una publicación de tecnología sellada pasa a no sellada** después de subir
  imágenes de referencia. Las imágenes de referencia se borran con la
  declaración, y la publicación pasa a exigir las ocho tomas.
- **La categoría desaparece del árbol** después de publicada. La publicación
  conserva la que tenía; la categoría se marca inactiva y no se puede elegir en
  borradores nuevos. No se reasigna nada de forma automática.
- **Subida interrumpida.** Una toma a medias no crea fila: la fila se escribe
  cuando los bytes están validados y guardados. Reintentar la misma posición
  reemplaza, nunca duplica.
- **Doble envío a revisión** por doble clic: el segundo recibe 409 y no crea una
  segunda solicitud.
- **El vendedor pierde la verificación con borradores abiertos.** Los borradores
  se conservan y no se pueden enviar a revisión mientras no vuelva a estar
  verificado (RN-013).
- **Ocho tomas subidas y el borrador abandonado.** Las imágenes ocupan espacio en
  el almacén público. Archivar un borrador borra sus tomas.
- **Título o descripción con caracteres de control o HTML.** Se guardan como
  texto plano; el frontend nunca los interpreta como marcado.

## Seguridad y datos

- Las tomas de producto van al **almacén público** (`PublicFileStore`), que es
  cacheable y va por CDN. Su clave es opaca y derivada de un identificador
  aleatorio, no del nombre del archivo ni de nada de la persona (ADR-0018).
- **Consecuencia que hay que aceptar a conciencia:** una toma de un borrador ya
  está en el almacén público antes de que la publicación sea visible. No está
  enlazada en ninguna parte y su clave no es adivinable, pero quien tenga la
  dirección la ve. Es aceptable porque es la foto de un producto que el vendedor
  va a publicar; no lo sería para nada del almacén reservado, y por eso la cédula
  y la selfie no pasan por aquí.
- **El EXIF se quita siempre.** Una toma publicada con su EXIF dice dónde vive el
  vendedor.
- El vendedor solo lee y escribe sus propias publicaciones. El moderador lee
  cualquiera. Nadie más lee una que no esté en `PUBLISHED`.
- Todo lo que escribe el vendedor se valida en el borde y se vuelve a validar en
  el dominio: longitudes, listas cerradas, rangos y proporción de imagen.

## Datos de referencia

Los nombres de código van en inglés; lo visible, por clave de Transloco.

### Grupos de medida — `MeasurementGroup`

Cada categoría declara uno. Las medidas del grupo son obligatorias, van en
centímetros y admiten un decimal.

| Código | Visible | Medidas obligatorias |
|---|---|---|
| `TOP` | Parte superior | Pecho, largo, hombros, largo de manga |
| `BOTTOM` | Parte inferior | Cintura, cadera, tiro, largo |
| `FULL_BODY` | Prenda entera | Pecho, cintura, cadera, largo |
| `FOOTWEAR` | Calzado | Largo de plantilla interna |
| `ACCESSORY_VOLUME` | Accesorio con volumen | Alto, ancho, profundidad |
| `ACCESSORY_FLAT` | Accesorio plano | Largo, ancho |
| `DEVICE` | Dispositivo | Alto, ancho, profundidad |

`FULL_BODY` y no `DRESS` porque cubre vestido, enterizo y overol; llamarlo
vestido obligaría a inventar un grupo más para los otros dos.

**El accesorio son dos grupos y no uno.** Alto, ancho y profundidad describen un
bolso y no describen una correa. La historia proponía un solo `ACCESSORY` y se
partió al dibujar el árbol, en `docs/producto/categorias.md`, que también anota la
arruga conocida de los sombreros.

Qué grupo declara cada categoría está en `docs/producto/categorias.md`.

### Sistemas de talla — `SizeSystem`

| Código | Visible | Valores |
|---|---|---|
| `ALPHA` | Talla por letra | XS, S, M, L, XL, XXL |
| `NUMERIC_CO` | Talla numérica | 4, 6, 8, 10, 12, 14, 16, 18, 20 |
| `WAIST_INCHES` | Talla de cintura en pulgadas | 26 a 46, de dos en dos |
| `FOOTWEAR_CO` | Talla de calzado | 33 a 46 |
| `ONE_SIZE` | Talla única | Un solo valor |

**Estos valores hay que confirmarlos con alguien que venda ropa en Colombia.**
Son la lista mínima defendible para poder escribir criterios verificables, no una
decisión de producto ya tomada. Corregirlos no cambia el esquema: `size_system` y
`size_value` lo soportan igual.

**Una categoría admite más de un sistema, no uno solo.** Sin eje de género, unos
jeans se venden en talla numérica y en pulgadas de cintura, así que la categoría
declara la lista de sistemas admisibles —`categories.size_systems`, en plural— y
el vendedor elige uno, que es el que queda en `products.size_system`. Salió al
aprobar el árbol; el criterio 6 se lee con eso: la talla es obligatoria y su
sistema tiene que ser uno de los que admite la categoría elegida.

### Colores — `Color`

Lista cerrada, porque es filtro de catálogo y en texto libre no filtra nada.

`BLACK`, `WHITE`, `GRAY`, `BEIGE`, `BROWN`, `RED`, `PINK`, `ORANGE`, `YELLOW`,
`GREEN`, `BLUE`, `PURPLE`, `GOLD`, `SILVER`, `MULTICOLOR`.

`MULTICOLOR` cubre estampados y combinaciones. No se añade «estampado» aparte: es
un patrón, no un color, y mezclarlos rompe el filtro.

### Motivos de rechazo de publicación — `ListingRejectionReason`

Lista cerrada. Cada uno sale de una regla; ninguno es decorativo.

| Código | Visible | De dónde sale |
|---|---|---|
| `PHOTOS_UNUSABLE` | Las fotos no permiten ver el producto | RN-016, RN-018, RN-019 |
| `PHOTOS_MISMATCH` | Las fotos no corresponden a lo descrito | RN-021, RN-050 |
| `MEASUREMENTS_UNRELIABLE` | Las medidas faltan o no son creíbles | RN-021 |
| `CONDITION_MISDECLARED` | La condición declarada no es la que se ve | RN-021, RN-050 |
| `PROHIBITED_ITEM` | Artículo no permitido en Sendik | RN-024 |
| `SUSPECTED_COUNTERFEIT` | Se sospecha réplica o falsificación | RN-024 |
| `PRICE_OUT_OF_RANGE` | El precio está fuera de lo razonable | RN-020 |

La nota opcional viaja al vendedor y **nunca contiene información de un tercero**,
por el mismo motivo que en HU-002.

### Plazo de revisión

`LISTING_REVIEW_DAYS`, con su valor por decidir. Va en configuración por lo mismo
que `VERIFICATION_REVIEW_DAYS`: cambiar una promesa no puede exigir un despliegue.
Mientras no se decida, la interfaz no promete plazo. Decir «pronto» es no decir
nada, y prometer un número inventado es peor.

### Transiciones de la publicación

La tabla que proponía esta historia ya es **RN-061**, en `reglas-negocio.md`, y
esa es su única fuente de verdad. Aquí no se repite: una tabla de transiciones en
dos archivos es una tabla que tarde o temprano dice dos cosas distintas.

Lo que la máquina de estados le debe a esta historia, y que las pruebas del
dominio tienen que cubrir, es que ninguna transición fuera de RN-061 exista, que
`SOLD` y `ARCHIVED` sean terminales, y que reanudar una publicación pausada no
pase por moderación.

## Diseño

- Formulario por pasos, con el mismo patrón de guardado de avance que
  `features/seller-verification`. Se reutilizan sus componentes de paso, su barra
  de progreso y su resumen antes de enviar.
- Móvil primero: es donde el vendedor fotografía y publica.
- Estados obligatorios de cada pantalla: cargando, vacío —«todavía no tienes
  publicaciones»—, error de red con reintento, y error de validación por campo.
- Colores y medidas por variable de `tokens.css`; el texto por clase de rol de
  `tipografia.css`. El acento bronce aparece una vez por pantalla, en la insignia
  de vendedor verificado, nunca como relleno del
  botón principal, y nunca como color de texto.
- La rejilla de las ocho tomas usa la proporción 3:4 de RN-018, que es la misma
  del catálogo.
- Ningún texto visible en la plantilla: todo por clave de Transloco.

## Notas técnicas

- **Módulo nuevo de dominio** `co.sendik.catalog`, con el mismo corte por capas
  que `co.sendik.identity`. El dominio no importa Spring ni JPA.
- **Migración `V9__catalog.sql`**, nueva. Crea `categories`, `products`,
  `listings`, `product_images` y `moderation_events` según
  `docs/arquitectura/modelo-datos.md`, con los índices que ese documento exige:
  `listings(status, published_at desc)`, `products(seller_id)` y
  `product_images(product_id, position)` único.
- Campos que el modelo de datos todavía no contempla y que esta historia
  necesita: `listings.requires_attention` y `listings.attention_reason`, y en
  `categories` las columnas del grupo de medida y del sistema de talla.
- **Bandera** `FEATURE_PUBLISHING`, ya declarada en `application.yaml` y en
  `docs/operacion/configuracion.md`, hoy apagada. Con ella apagada los endpoints
  responden 404, no 403.
- **Endpoints.** Se sigue la convención real del código, que nombra la acción
  como sustantivo —`/submission`, `/approval`, `/rejection`— y no como verbo:

  | Método y ruta | Quién | Qué hace |
  |---|---|---|
  | `POST /api/v1/listings` | Vendedor verificado | Crea el borrador |
  | `GET /api/v1/listings/{id}` | Dueño, moderador, o cualquiera si está publicada | Lee una publicación |
  | `PATCH /api/v1/listings/{id}` | Dueño | Guarda datos del producto |
  | `POST /api/v1/listings/{id}/images` | Dueño | Sube una toma, multipart. El cuerpo lleva el `kind` |
  | `DELETE /api/v1/listings/{id}/images/{imageId}` | Dueño | Borra una toma o una imagen de referencia |
  | `POST /api/v1/listings/{id}/submission` | Dueño | Envía a revisión |
  | `DELETE /api/v1/listings/{id}/submission` | Dueño | Retira la solicitud |
  | `POST /api/v1/listings/{id}/approval` | Moderador | Aprueba |
  | `POST /api/v1/listings/{id}/rejection` | Moderador | Rechaza con motivo |
  | `POST /api/v1/listings/{id}/pause` | Dueño | Pausa |
  | `DELETE /api/v1/listings/{id}/pause` | Dueño | Reanuda |
  | `POST /api/v1/listings/{id}/archival` | Dueño o moderador | Archiva |
  | `GET /api/v1/users/me/listings` | Dueño | Sus publicaciones, paginado |

  `docs/arquitectura/contrato-api.md` citaba `/listings/{id}/submit-for-review`
  como ejemplo de excepción con verbo. El código no hace eso en ninguna ruta, y
  manda el código: la línea ya está corregida.
- **Códigos de error nuevos**, prefijo `CATALOG_`: `CATALOG_SELLER_NOT_VERIFIED`,
  `CATALOG_LISTING_INVALID_STATE`, `CATALOG_SHOTS_INCOMPLETE`,
  `CATALOG_LISTING_NOT_EDITABLE`, `CATALOG_SELF_MODERATION_FORBIDDEN`,
  `CATALOG_UNKNOWN_CATEGORY`, `CATALOG_CONDITION_NOT_ALLOWED` y
  `CATALOG_REFERENCE_IMAGE_NOT_ALLOWED`. Se agregan al enum del backend y al archivo de
  traducción del frontend **en el mismo commit**. Los `FILE_*` ya existen y se
  reutilizan tal cual.
- **Frontend:** `features/listing`, con su dominio, su infraestructura y sus
  páginas, siguiendo el corte de `features/seller-verification`. El guard de rol
  de ADR-0021 se reutiliza para las rutas de moderación.
- Las claves de Transloco van bajo `listing.*`. El texto todavía no existe:
  `textos-web.md` no tiene sección de publicación de producto y hay que escribirla
  antes de implementar la interfaz.
- Bloqueo optimista con la columna `version` de `listings`, que el modelo de datos
  ya contempla.

## Pruebas requeridas

- **Dominio**, sin Spring: la máquina de estados completa de la tabla de
  transiciones, incluida cada transición inválida; la regla de las ocho tomas y
  las cuatro canónicas, con su excepción de cuatro para la tecnología sellada; el
  conjunto de medidas obligatorias por grupo; el rango de precio y la marca de
  atención; y que una categoría sin `allows_used` rechace las tres condiciones de
  segunda.
- **Dominio, tecnología**: que una imagen de referencia nunca cuente como toma,
  que solo se admita en tecnología sellada, y que al dejar de estar sellada la
  publicación vuelva a exigir ocho tomas. Estas tres son las que impiden que una
  publicación se arme con fotos del fabricante.
- **Aplicación**: que un vendedor no verificado no pueda crear, que uno revocado
  no pueda crear pero conserve lo publicado, y que editar contenido devuelva a
  revisión mientras editar precio no.
- **Integración del repositorio**: unicidad de `(product_id, position)`, el índice
  de la bandeja, y que el bloqueo optimista produzca 409.
- **Integración de la subida**: tipo real por bytes de cabecera con un archivo
  disfrazado, EXIF eliminado comprobado sobre el archivo ya guardado, proporción y
  dimensiones mínimas, y los tres códigos `FILE_*` con sus tres estados.
- **Web del backend**: 401, 403 y 404 con la bandera apagada, por cada endpoint.
- **Componente en el frontend**: validación por campo, guardado de avance, y los
  estados de carga, vacío y error.
- **Extremo a extremo**: crear borrador, subir ocho tomas desde galería, enviar a
  revisión, aprobar como moderador y comprobar que queda visible; y el mismo
  camino con rechazo, corrección y reenvío.
- **Accesibilidad** del formulario con axe, en modo claro y oscuro, según
  ADR-0016.

## Lo que se cerró al escribir esta historia

Todo esto ya está en el repositorio, hecho el 24 de agosto de 2026. Se anota aquí
para que quien implemente no lo vuelva a abrir.

- **RN-061**, transiciones válidas de la publicación. Era el hueco equivalente a
  RN-059 y a RN-044: el glosario listaba los siete estados y ninguna regla decía
  quién pasa de cuál a cuál.
- **RN-062**, qué edición devuelve una publicación visible a moderación. Resuelve
  la contradicción de texto entre RN-015 y RN-030.
- **RN-063**, un moderador no decide sobre su propia publicación. Es RN-060
  aplicada al catálogo.
- **RN-020 aclarada**: el rango de precio es blando y la marca de atención es la
  consecuencia de salirse. La frase sola admitía las dos lecturas.
- **Glosario**: Borrador, Sistema de talla, Grupo de medida, Color y Motivo de
  rechazo de publicación, más la nota de que `PAUSED` es del vendedor y
  `ARCHIVED` de los dos.
- **Modelo de datos**: `listings.requires_attention`, `listings.attention_reason`
  y `listings.rejection_note`; `categories.measurement_group` y
  `categories.active`; y la nota de que el contenido de `products.measurements` lo
  determina el grupo de medida de la categoría.
- **La nota técnica de HU-003** sobre la URL firmada, corregida contra ADR-0018.
- **La línea de `contrato-api.md`** que citaba `/listings/{id}/submit-for-review`,
  una ruta que nunca existió.

## Lo que todavía falta

**Bloquea la implementación:**

- **La sección «Publicación de producto — Fase 2» de `textos-web.md`**, de donde
  salen las claves `listing.*`. Bloquea la interfaz, no el backend: ningún texto
  visible se escribe en la plantilla. La migración, el dominio y los endpoints se
  pueden hacer sin ella.

El árbol de categorías **ya no bloquea**: se aprobó el 24 de agosto de 2026 y está
en `docs/producto/categorias.md`, con el grupo de medida y los sistemas de talla
de cada una de sus veinticuatro categorías. Al dibujarlo cambiaron dos cosas de
esta historia, ya aplicadas: `categories.size_systems` es plural, y el grupo
`ACCESSORY` se partió en `ACCESSORY_VOLUME` y `ACCESSORY_FLAT`.

**No bloquea, pero conviene decidirlo antes de encender la bandera:**

- **Los valores de talla** de la tabla de referencia, que hay que confirmar con
  alguien que venda ropa en Colombia. Corregirlos no toca el esquema.
- **Los nombres visibles de las veinticuatro categorías en inglés**, que van en la
  migración que las siembra.
- **`LISTING_REVIEW_DAYS`**, o la decisión de no prometer plazo. Mientras no se
  decida, la interfaz no promete nada.
- **Si hay límite de publicaciones activas por vendedor**, ya anotado como
  pendiente en `textos-web.md`. Sin decisión no hay límite, y eso también es una
  decisión.
- **Plazo máximo de despacho del vendedor**, anotado en `textos-web.md`. No toca
  esta historia; sí la Fase 3.
