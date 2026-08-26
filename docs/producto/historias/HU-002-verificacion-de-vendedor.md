# HU-002 — Verificación de vendedor

**Fase:** 2 | **Estado:** hecha
**Reglas:** RN-010 a RN-014, RN-046, RN-059

## Decisiones tomadas el 21 de agosto de 2026

Cuatro cosas que esta historia daba por resueltas y no lo estaban. Se decidieron
antes de escribir código, porque tres de ellas deciden el esquema o el nombre del
actor:

- **Revisa el moderador**, y su definición en el glosario se amplió para decirlo:
  antes era «aprueba o rechaza publicaciones y nada más». Es el único rol que ve la
  cédula y la selfie de alguien.
- **Las imágenes no se sirven por URL firmada.** Las sirve un endpoint autenticado,
  autorizado por rol y que registra cada lectura. El criterio 2 de más abajo pedía
  URL firmada; se cambió porque contradecía ADR-0018 y porque una URL firmada, en
  el tiempo que vive, deja ver la cédula a quien la tenga sin pasar por ninguna
  autorización nuestra y sin dejar rastro en la bitácora que esta misma historia
  exige.
- **El cifrado de columna es ADR-0020**: AES-GCM en la aplicación, con un HMAC de
  clave propia para la unicidad del criterio 5. Un valor cifrado no se puede
  indexar, así que sin ese HMAC el criterio 5 no se puede cumplir.
- **Falta el estado `REVOKED`** y se agregó al glosario. RN-013 pide revocar un
  sello ya otorgado, y eso no es `REJECTED`. Las transiciones completas están en
  RN-059.

## Objetivo

Un usuario se convierte en vendedor verificado entregando documento de identidad,
selfie y cuenta bancaria, para poder publicar y recibir desembolsos.

## Alcance

Entra: captura de documento por ambas caras, selfie con prueba de vida básica,
registro y validación de cuenta bancaria, revisión manual, sello de verificado.

No entra: proveedor automático de verificación de identidad, verificación de
empresas, validación de antecedentes.

## Criterios de aceptación

1. Solo un usuario con correo verificado y mayor de edad puede iniciar el
   proceso.
2. Se captura el documento por ambas caras con guía de encuadre y detección de
   desenfoque; una imagen borrosa se rechaza en el cliente antes de subirla.
3. La selfie se toma en el momento con la cámara, y la interfaz no ofrece cargar
   un archivo. **El límite se dice aquí y no se promete más:** en un navegador esto
   no se puede garantizar. Se usa la cámara y no se ofrece el selector de archivos,
   pero una cámara virtual pasa por ahí. Lo que el criterio exige es que cargar
   desde la galería no esté ofrecido, no que sea imposible.
4. Los datos bancarios exigen banco, tipo de cuenta, número y titular. El titular
   debe coincidir con el nombre del documento; si no coincide, se rechaza con
   motivo explícito.
5. Un mismo número de documento no puede quedar verificado en dos cuentas.
6. Enviada la solicitud, el estado pasa a `PENDING_REVIEW` y se informa el tiempo
   estimado de respuesta.
7. El moderador aprueba o rechaza indicando motivo de una lista cerrada más una
   nota opcional.
8. Aprobado, el usuario obtiene el rol de vendedor, el sello visible y acceso a
   publicar.
9. Rechazado, puede corregir y reenviar hasta tres veces; el cuarto intento
   requiere revisión manual y se avisa así.
10. Cada cambio de estado se notifica por correo.
11. En ninguna respuesta de la API aparecen las imágenes, el número de documento
    completo ni el número de cuenta completo. Solo los cuatro últimos dígitos.

## Casos borde

- Salir a la mitad del proceso: se guarda el avance y se retoma donde iba.
- Documento vencido: se rechaza con motivo específico.
- Cámara denegada en el navegador: se explica cómo habilitarla y no se ofrece
  carga desde galería para la selfie.
- Reintento con los mismos datos rechazados: se detecta y se avisa.

## Seguridad y datos

- Las imágenes se guardan en el almacén reservado —cubo privado, sin lectura
  pública, ADR-0018— y **no se sirven por ninguna URL**. Las entrega un endpoint que
  comprueba el rol de moderación y registra la lectura antes de devolver los bytes.
- El número de documento y el de cuenta se guardan cifrados a nivel de columna
  según ADR-0020, con `last_four` en claro para lo que la pantalla muestra.
- Todo acceso a estos datos queda registrado en bitácora con actor y motivo. La
  bitácora es la razón de que no haya URL firmada: un enlace que funciona por sí
  solo no puede registrar quién lo usó.
- Retención: se define en `docs/operacion/datos-personales.md`. No se conservan
  indefinidamente.

## Datos de referencia

Decididos el 21 de agosto de 2026. Los nombres de código van en inglés; lo visible,
por clave de Transloco.

### Documentos admitidos — `IdentityDocumentType`

| Código | Visible |
|---|---|
| `CC` | Cédula de ciudadanía |
| `CE` | Cédula de extranjería |
| `PPT` | Permiso por Protección Temporal |

Sin pasaporte: no se pidió y no se agrega solo. **El formato del número por tipo no
está decidido**, así que la validación es la mínima defendible —solo dígitos y un
rango de longitud— y queda anotada como regla por endurecer. Inventar el formato de
un PPT sería inventar una regla de negocio.

### Motivos de rechazo — `RejectionReason`

Lista cerrada. Los cuatro primeros los exige la propia historia, no son opcionales:

| Código | Visible | De dónde sale |
|---|---|---|
| `ILLEGIBLE_PHOTOS` | Las fotos no se pueden leer | Decidido |
| `EXPIRED_DOCUMENT` | El documento está vencido | Casos borde |
| `HOLDER_MISMATCH` | El titular de la cuenta no coincide con el documento | Criterio 4, RN-012 |
| `DOCUMENT_ALREADY_VERIFIED` | Ese documento ya está verificado en otra cuenta | Criterio 5, RN-010 |
| `REQUIREMENTS_NOT_MET` | No cumple los requisitos para vender | Decidido |

**`REQUIREMENTS_NOT_MET` es genérico a propósito, y eso tiene un costo que se
asume.** Cubre el caso de antecedentes penales o judiciales sin registrar nada
judicial sobre la persona: el sistema no consulta ninguna fuente judicial —la
historia deja la validación de antecedentes fuera de alcance— y
`docs/operacion/datos-personales.md` no tiene categoría para ese dato. Lo que se
pierde es que la persona rechazada no sabe por qué, y eso es deliberado, no un
olvido.

**La nota opcional del criterio 7 es la puerta de atrás de esta decisión.** Es texto
libre, así que es exactamente donde alguien podría escribir lo que se decidió no
guardar. Regla: la nota viaja a la persona rechazada y **nunca contiene información
judicial ni datos de un tercero**. Si hace falta registrar algo así, no se escribe
en la nota: se decide primero, con su clasificación y su respaldo.

### Tipos de cuenta — `BankAccountType`

| Código | Visible |
|---|---|
| `SAVINGS` | Ahorros |
| `CHECKING` | Corriente |
| `ELECTRONIC_DEPOSIT` | Depósito electrónico |

El tercero es el de Nequi, DaviPlata, Movii, Dale y RappiPay, que no son cuentas de
ahorros aunque se parezcan al usarlas. La distinción no es cosmética: el desembolso
de la Fase 3 necesita el tipo correcto o el dinero no llega.

### Bancos y billeteras

Van en **tabla con migración de Flyway**, no en un enum del código ni en una
variable: son 29 y crecerán, y la Fase 3 necesita el código de cada uno para el
desembolso. Cada fila lleva un código interno estable, el nombre visible y si es
banco o billetera. Guardar el nombre en la fila del vendedor ataría el dato a cómo
se llama hoy la entidad.

Bancos: Bancolombia, Banco Davivienda, Banco de Bogotá, BBVA Colombia, Banco AV
Villas, Banco Caja Social, Scotiabank Colpatria, Banco Popular, Banco de Occidente,
Banco GNB Sudameris, Banco Agrario, Banco Itaú, Citibank, Banco Pichincha, Banco
Santander, Bancoomeva, Banco Falabella, Banco Serfinanza, Bancamía, Banco Mundo
Mujer, Lulo Bank.

Billeteras y depósitos electrónicos: DaviPlata, Dale, RappiPay, Nequi, Movii, Tpaga,
Ualá.

**Scotiabank y Colpatria se listaron por separado y aquí van como una sola entrada**,
Scotiabank Colpatria, porque son la misma entidad: dos opciones que son una obligan
a la persona a adivinar. Si en tu operación son dos cosas distintas, se separan.

### Tiempo de respuesta

`VERIFICATION_REVIEW_DAYS`, dos días hábiles. Texto: «revisamos tu solicitud en
máximo 2 días hábiles». Va en configuración por lo mismo que `CLAIM_WINDOW_DAYS`:
cambiar la promesa no puede exigir un despliegue.

Cada reenvío del criterio 9 vuelve a `PENDING_REVIEW` y promete otra vez el mismo
plazo. El cuarto intento de RN-014, que exige revisión manual, también: no se
prometió nada distinto y no se inventa aquí.

## Lo que todavía falta

El texto de las pantallas ya está escrito: `textos-web.md` §«Verificación de
vendedor — Fase 2» y las claves `sellerVerification.*` en `src/i18n`.

Queda abierto, y no bloquea la historia:

- **El formato del número por tipo de documento**, según la nota de arriba. Hoy
  se valida el mínimo —dígitos y rango de longitud— y está anotado como regla
  por endurecer cuando se decida.
- **`VIEW_BANK_ACCOUNT` no lo usa nadie.** La acción existe en la bitácora, pero
  el criterio 11 no exceptúa al moderador, así que tampoco él ve el número
  completo y no hay lectura que registrar. Se deja porque el desembolso de la
  Fase 3 sí tendrá que leerlo.
- **La interfaz de la bandeja de revisión** es el punto «panel de moderación» de
  la Fase 2 y no entra en esta historia, cuyos cinco endpoints de revisión sí
  están y probados. La escribe HU-006.

Resuelto después, por HU-006: el rol de moderador ya no se otorga solo a mano.
`SECURITY_BOOTSTRAP_MODERATORS` lo concede al arrancar a los correos que se
configuren, sobre cuentas que ya existen. El `INSERT` de más abajo sigue siendo
válido y es lo que esa variable hace por dentro.

## Notas técnicas

Endpoints del lado de quien se verifica, todos bajo `/api/v1/users/me/verification`:

| Método y ruta | Qué hace |
|---|---|
| `POST /` | Inicia, o devuelve la solicitud en curso. Idempotente, responde 200 |
| `GET /` | El estado propio. 404 mientras no haya empezado |
| `PUT /document` | Las dos caras, multipart. Sustituye lo anterior |
| `PUT /selfie` | La selfie, multipart |
| `PUT /bank-account` | La cuenta, JSON |
| `POST /submission` | Envía a revisión. Cuenta un intento de RN-014 |

Van bajo `users/me` y no bajo `sellers` porque quien llama **todavía no es vendedor**,
y la ruta de un recurso no puede depender del resultado de la operación que se le pide.

Y uno más, que no cuelga de la solicitud porque es un catálogo:

| Método y ruta | Qué hace |
|---|---|
| `GET /api/v1/financial-institutions` | Bancos y billeteras activos, para el desplegable del formulario de cuenta |

Ruta propia y no bajo la solicitud, porque el catálogo no es parte de la solicitud
de nadie: es la tabla de `V7`. **Exige token pero no rol** —no hay nada personal ahí,
son los mismos nombres de bancos para todo el mundo— y está detrás de la misma
bandera que el resto. Devuelve el `code`, que es lo estable; el `name` es lo que
cambia cuando dos entidades se fusionan. Cuando la Fase 3 lo necesite para el
desembolso, lo pedirá aquí mismo.

**Los endpoints solo existen con `FEATURE_SELLER_VERIFICATION` encendida.** Sin la
bandera el controlador no se crea y las rutas responden 404: no es que rechacen, es que
no están, que es para lo que existen las banderas. Un 403 le diría a cualquiera que la
funcionalidad está ahí.

**Corregido el 26 de agosto de 2026.** Eso era cierto para el controlador y no para la
cadena de seguridad: la regla `hasRole("MODERATOR")` de `/api/v1/verifications/**` se
evalúa en el filtro, antes de que nadie busque un manejador, así que con la bandera
apagada las rutas de revisión respondían **403** —y un 403 confirma que la funcionalidad
está ahí, que es justo lo que la bandera existe para no decir—. La regla ahora solo se
declara si la funcionalidad está expuesta. Lo encontró la prueba que HU-007 escribió para
su propia bandera; ninguna prueba de esta historia lo miraba.

### Endpoints del moderador

Bajo `/api/v1/verifications`, **fuera de `users/**` a propósito**: esa ruta solo exige
token, y con estos endpoints allí cualquiera con una cuenta podría aprobar su propia
verificación. Aquí la regla es `hasRole("MODERATOR")`.

| Método y ruta | Qué hace |
|---|---|
| `GET /` | La bandeja: lo que espera revisión, lo más viejo primero |
| `GET /{id}/images/{imagen}` | Una de las tres imágenes, con la lectura anotada en bitácora |
| `POST /{id}/approval` | Aprueba. Otorga el rol de vendedor (criterio 8) |
| `POST /{id}/rejection` | Rechaza con motivo de la lista cerrada y nota opcional |
| `POST /{id}/revocation` | Revoca el sello de quien ya lo tenía (RN-013) |

**Dos cerraduras, no una.** La regla por ruta en `SecurityConfig` y un `@PreAuthorize`
en cada método. Es redundante a propósito: mover un endpoint de sitio no se lleva su
autorización por delante. Ojo con que `@PreAuthorize` **solo funciona con
`@EnableMethodSecurity`**; sin esa anotación Spring lo ignora en silencio y el método se
lee como protegido sin estarlo.

**Las imágenes se sirven por ese endpoint y no por URL firmada.** Es la decisión de
ADR-0018 aplicada: un enlace que funciona por sí solo no puede registrar quién lo usó, y
esta historia exige bitácora con actor y motivo. Se pide «el frente de esta solicitud», no
una clave de archivo: con la clave en la URL, quien la tuviera podría pedir cualquier cosa
del almacén reservado. Las respuestas van con `Cache-Control: no-store`.

**Una tensión del criterio 11 que conviene mirar.** Ese criterio no hace excepciones por
rol, así que el moderador tampoco ve el número de documento completo: compara la imagen
contra los cuatro últimos dígitos. Serví las imágenes porque la sección de seguridad de
esta historia dice expresamente que se ven «solo para el rol de moderación», pero **los
números no**. Si revisar exige el número entero, hay que decidirlo y anotarlo: la acción
`VIEW_BANK_ACCOUNT` ya existe en la bitácora para ese día y hoy no la usa nadie.

### Los correos del criterio 10

Cuatro avisos, uno por cambio de estado que la persona necesita saber:

| Cuándo | Qué dice |
|---|---|
| `PENDING_REVIEW` | Recibimos la solicitud y la revisamos en máximo `{{días}}` días hábiles |
| `VERIFIED` | Ya es vendedor verificado y su perfil muestra el sello |
| `REJECTED` | El motivo de la lista cerrada, la nota, y cuántos intentos quedan |
| `REVOKED` | El motivo, y que lo publicado sigue visible pero no puede crear más (RN-013) |

**No hay aviso de empezar el proceso**, aunque `NOT_STARTED → IN_PROGRESS` también sea
un cambio de estado y el criterio diga «cada cambio». Lo provoca la propia persona
pulsando un botón y lo ve en pantalla en el momento: un correo ahí no informa de nada y
enseña a ignorar los nuestros. Si se quiere, es una línea.

**En cero intentos el correo de rechazo no invita a reintentar.** RN-014 no lo permite,
así que decir «vuelve a intentarlo» sería mandar a alguien a una negativa. Dice que
escriba.

**El aviso de revocación es propio y no el de rechazo**, por lo mismo que son dos
estados distintos: a quien nunca pasó la revisión se le dice que corrija; a quien la
pasó y perdió el sello hay que decirle qué pasa con lo que ya publicó.

Ninguno de los cuatro puede tumbar la operación: el puerto de correo no lanza (ADR-0012)
y los avisos se mandan **después** de guardar y de anotar en la bitácora. Un aviso de
algo que no se guardó es peor que no avisar.

### Cómo se otorga el rol de moderador

**No hay mecanismo, y es una decisión.** Nadie otorga `MODERATOR` desde ninguna pantalla:
el panel administrativo que lo haría es de Fase 4 (`alcance.md`). Hasta entonces se otorga
a mano, una vez, con acceso a la base:

```sql
INSERT INTO user_roles (user_id, role, granted_at)
SELECT id, 'MODERATOR', now() FROM users WHERE email = 'quien-revisa@sendik.co'
ON CONFLICT (user_id, role) DO NOTHING;
```

Lo descartado y por qué: **no se otorga desde configuración al arrancar**. Una variable
con correos que reciben el rol de moderación convierte una variable de entorno en un
otorgamiento de privilegios, y quien pueda editar la configuración del despliegue pasa a
poder verse las cédulas de todo el mundo. Una sentencia a mano deja rastro y exige acceso
a la base, que es un permiso distinto y más difícil de conseguir por accidente.

## Pruebas requeridas

- Unitarias: coincidencia de titular, conteo de intentos, transiciones de estado.
- Integración: unicidad de documento bajo concurrencia, cifrado efectivo en base
  de datos, ausencia de datos sensibles en las respuestas.
- Extremo a extremo: recorrido completo hasta obtener el sello.

### Cómo quedó repartido el extremo a extremo

El recorrido está cubierto entero, pero en dos piezas, y no por comodidad:

- `frontend/e2e-completo/verificacion-de-vendedor.spec.ts` recorre **la mitad de la
  persona por el navegador**, con el backend y PostgreSQL de verdad: empezar,
  documento con las dos caras, selfie, cuenta, y enviar a revisión hasta
  `PENDING_REVIEW`. También el caso borde de retomar tras recargar, la negativa de
  RN-012 traducida en pantalla y el criterio 11 sobre el HTML que llega. La cámara
  es la falsa de Chromium.
- `backend/bootstrap/.../SellerVerificationJourneyTest.java` recorre **la cadena
  hasta el sello** contra PostgreSQL real: aprobar, el rol `SELLER`, la bitácora, y
  además RN-013, RN-014 y el criterio 5 de punta a punta por los mismos casos de
  uso que usa el borde.

El corte está donde está porque aprobar exige un moderador y el rol se otorga con
la sentencia SQL de más arriba —no hay pantalla—. Darle a la suite de navegador
acceso a la base significaría agregarle al frontend un cliente de PostgreSQL, que
es una dependencia nueva para una sola prueba.

**Lo que encontró.** La captura estaba rota en cualquier navegador: el visor vive
dentro de un `@if`, así que al volver de `getUserMedia` el elemento todavía no
existía y `srcObject` se asignaba sobre nada. La cámara quedaba concedida y
encendida, sin imagen, y tomar la foto fallaba por un fotograma de cero por cero.
Ninguna prueba de componente podía verlo: todas usan un doble de la cámara que no
necesita un elemento de verdad. Corregido enganchando el flujo cuando el elemento
aparece, con su prueba de regresión en `capture-field.spec.ts`.
