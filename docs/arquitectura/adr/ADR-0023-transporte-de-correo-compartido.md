# ADR-0023 — Transporte de correo compartido entre contextos

**Fecha:** 2026-08-26
**Estado:** aceptada

## Contexto

Hasta HU-006, el único contexto que mandaba correo era `identity`: verificación
de la cuenta, restablecimiento de contraseña, avisos de seguridad y las cuatro
notificaciones de la verificación de vendedor. Su puerto de salida
`MailSender` está en `application/co/sendik/identity/port/out` y su firma habla
de identidad: recibe un `User` y nombra lo que ocurrió (`enviarAvisoDeVerificacionAprobada`).

HU-007 trae el segundo contexto que necesita escribir. El criterio 26 exige
avisar al vendedor de cada decisión del moderador, y `catalog` declaró su propio
puerto, `ListingNotifier`, cuyo javadoc dice literalmente: «puerto propio de
catalog y no el `MailSender` de identity, por lo mismo que `SellerEligibility`:
un contexto no usa el puerto de otro».

El problema no era el puerto sino el adaptador. Para mandar el correo de verdad
hacía falta un transporte, y el único que existía era el de identidad. La primera
implementación agregó tres métodos a `MailSender` —aprobada, rechazada,
retirada—, con parámetros de tipo `String` para no atar identidad al modelo del
catálogo.

Eso dejó una situación que la revisión de arquitectura marcó como grave y que es
fácil de comprobar: **los tres métodos nuevos del puerto de identidad no los
llamaba ningún caso de uso de identidad.** Su único consumidor era
`co.sendik.catalog.client.MailListingNotifier`. La capa de aplicación de un
contexto había crecido tres operaciones que existían solo para servir a otro, sus
tres adaptadores habían crecido comportamiento de publicaciones, y los textos de
los correos del catálogo vivían en `co.sendik.identity.client`.

`docs/arquitectura/vision-tecnica.md` admite dos formas de comunicación entre
contextos: un caso de uso público o un evento de dominio. Un `port/out` de la
capa de aplicación ajena no es ninguna de las dos.

## Opciones

**A. Dejarlo como estaba: tres métodos de catálogo en `MailSender`.** Cero
trabajo adicional y funciona. El costo es el que describe el contexto: el
vocabulario de un contexto dentro del puerto de otro, y una regla escrita en el
repositorio que el código contradice. El costo crece con cada contexto que
necesite correo: `order` y `payment` lo necesitarán en Fase 3, y con esta opción
`MailSender` acabaría siendo el puerto de correo de todo el sistema, con el
nombre y el paquete de uno solo.

**B. Extraer un transporte a `shared`.** Un puerto nuevo,
`co.sendik.shared.port.out.MailTransport`, con un único método
`enviar(destinatario, asunto, cuerpoHtml)`. Los adaptadores que ya existen
—`ResendMailSender`, `ConsoleMailSender` y el envoltorio asíncrono
`AsyncMailSender`— pasan a implementarlo además de `MailSender`; en los dos
primeros el método privado `enviar` que ya tenían **es** el puerto, así que el
cambio en la clase es la firma y la anotación. Cada contexto arma sus propios
textos y los entrega.

**C. Un caso de uso público de identidad, del estilo `SendMailUseCase`.** Encaja
con la letra de `vision-tecnica.md`, pero convierte a identidad en el servicio de
correo del sistema: los demás contextos dependerían de su capa de aplicación para
algo que no tiene nada que ver con identidad. Es la opción A con otro nombre.

## Decisión

La opción B. El correo se manda por `MailTransport`, un puerto de `shared`;
`MailSender` vuelve a hablar solo de identidad y cada contexto arma el texto de
sus propios correos.

## Motivo

**Mandar un correo no es una responsabilidad de ningún contexto de negocio.** Es
un mecanismo, como guardar un archivo. El proyecto ya resolvió exactamente esto
para los archivos: `PublicFileStore` y `RestrictedFileStore` viven en
`co.sendik.shared.port.out` y los usan identidad —la cédula, el avatar— y
catálogo —las tomas de producto— sin que ninguno pase por el puerto del otro.
`MailTransport` es la misma decisión aplicada al correo, y la coherencia con un
precedente que ya funciona vale más que ahorrarse el cambio.

**Lo que cada correo dice sí es de cada contexto, y ahí no se comparte nada.**
El transporte recibe texto ya armado y no sabe de verificaciones ni de
publicaciones. `VerificationMailTexts` se queda en `identity.client` y
`ListingMailTexts` se muda a `catalog.client`, junto a `ListingRejectionTexts`,
que es quien traduce los siete motivos de rechazo. Un contexto nuevo no toca nada
de esto: escribe sus textos y llama al transporte.

**El destinatario viaja como cadena y no como `Email`.** Ese objeto de valor es
del modelo de `identity`, y un puerto de `shared` que lo nombrara volvería a atar
los contextos por otro sitio. Quien llama ya tiene la dirección validada, porque
la sacó de una cuenta.

## Consecuencias

**Se gana** que el segundo, el tercero y el cuarto contexto que necesiten correo
no dependan de identidad, y que el puerto de identidad vuelva a describir lo que
identidad hace. Se gana también que el envío en diferido siga aplicando a todos:
`AsyncMailSender` implementa los dos puertos y difiere las dos cosas, así que un
aviso de moderación no le suma la latencia del proveedor a la petición del
moderador —que era la razón de seguridad por la que ese envoltorio existe—.

**Se acepta perder** dos cosas. Una, que `AsyncMailSender` pide el mismo bean dos
veces, una por cada puerto: es explícito a propósito, y la alternativa era un
`instanceof` con una conversión, que es lo mismo escondido. Dos, que un contexto
que arme mal su HTML no tiene a nadie que lo revise por él; el transporte no
valida el cuerpo. A cambio, cada contexto escapa lo que inserta, que es donde esa
responsabilidad pertenece.

Este cambio **no toca el grafo de módulos de Gradle** ni agrega dependencias:
`MailTransport` vive en `application`, junto a los puertos de archivo, y lo
implementan las mismas tres clases de `infrastructure` que ya existían.

## Cuándo revisar

Si algún día el correo deja de ser el único canal —notificaciones push, mensajes
dentro de la aplicación— y varios contextos necesitan elegir canal según la
preferencia de la persona. Ahí la decisión no es de transporte sino de
enrutamiento, y este puerto se queda corto: haría falta algo que reciba un aviso
y decida por dónde sale. `ListingNotifier` ya está escrito pensando en eso; su
javadoc dice que el día que haya push, entra por ahí sin tocar ningún caso de uso.
