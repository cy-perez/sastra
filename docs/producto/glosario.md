# Glosario

Este archivo manda sobre cómo se llaman las cosas. El término en inglés es el
que se usa en el código; el término en español es el que se usa al hablar, en la
documentación y en la interfaz. Si un concepto no está aquí, no existe todavía:
se agrega antes de escribirlo en código.

**La columna de código no es la traducción de la interfaz.** El sitio también se
sirve en inglés, y ahí manda que se entienda, no que coincida con el
identificador. `NonConformingProduct` es el nombre de la clase; a la persona se
le dice *not grounds for a report*, porque "non-conforming product" en inglés
comercial suena a norma industrial. Igual con `SpinViewer`, que en pantalla es
*360° view*. Lo que **no** cambia entre idiomas es lo que se promete: las dos
versiones se sirven en la misma dirección y en Colombia lo anunciado es exigible,
se anuncie en el idioma que se anuncie.

## Personas

| Español | Código | Definición |
|---|---|---|
| Usuario | `User` | Cuenta con credenciales. Puede ser comprador, vendedor o ambos. |
| Comprador | `Buyer` | Rol del usuario cuando compra, y también cuando guarda algo para volver a ello. En el contexto `catalog` su identificador es `BuyerId`, propio de ese contexto como `SellerId` y `ModeratorId`. Marcar un favorito no es comprar todavía: la palabra nombra el rol al que apunta el gesto, no una transacción hecha. |
| Vendedor | `Seller` | Rol del usuario cuando publica y vende. Persona natural. |
| Vendedor verificado | `VerifiedSeller` | Vendedor que superó identidad, selfie y validación bancaria. Lleva la insignia de vendedor verificado, que es la única pieza de la interfaz que va en bronce (`docs/marca/manual.md`). |
| Moderador | `Moderator` | Persona de Sendik que aprueba o rechaza publicaciones y verificaciones de vendedor. Es el único rol que ve la cédula y la selfie de alguien, y solo a través de un endpoint que registra cada lectura (RN-046, ADR-0018). |
| Administrador | `Admin` | Persona de Sendik con acceso a la operación completa: configuración, cuentas y resolución de disputas. No es un moderador con más permisos: el moderador decide sobre lo que se publica y sobre quién queda verificado, y nada más. |

Un usuario tiene una sola cuenta. Ser vendedor no es otra cuenta: es un rol
adicional que se activa al completar la verificación.

Los cuatro roles son los valores de `Role` y del `CHECK` de `user_roles`. Los dos
últimos existen en el esquema desde Fase 1 y todavía no los otorga nadie: el
panel que los usa llega en Fase 4 (`docs/producto/alcance.md`).

## Catálogo

| Español | Código | Definición |
|---|---|---|
| Producto | `Product` | Lo que se publica y se vende. Unidad de venta, existencia siempre 1. Es el término general y cubre las dos familias que Sendik admite: moda y tecnología. |
| Prenda | `Garment` | Producto de moda. Se dice «prenda» **solo** en moda; en un texto que hable de las dos cosas se dice producto. |
| Dispositivo | `Device` | Producto de tecnología: celular, computador, televisor y demás. Solo se vende nuevo (RN-064). |
| Sellado | `Sealed` | Dispositivo que conserva el empaque de fábrica sin abrir. Se declara, habilita las imágenes de referencia y rebaja las tomas exigidas a las cuatro del empaque (RN-065). Recibir abierto lo declarado sellado es producto no conforme. |
| Imagen de referencia | `ReferenceImage` | Foto del producto que no tomó el vendedor, normalmente del fabricante. Solo existe en tecnología sellada, nunca sustituye a las tomas reales y siempre va rotulada como referencia (RN-066). |
| Garantía del fabricante | `ManufacturerWarranty` | Meses de garantía que el dispositivo trae de fábrica. La declara el vendedor y responde el vendedor. **No es el Respaldo** y las dos palabras no se mezclan (RN-067). |
| Publicación | `Listing` | El producto junto a su estado de moderación y visibilidad. |
| Moderación | `Moderation` | Revisión de una publicación antes de que sea visible. Sin excepciones (RN-015). |
| Retiro de publicación | `ListingRemoval` | El moderador baja algo que ya era visible, por RN-024. Termina en `ARCHIVED`, igual que archivar, pero no es lo mismo: el vendedor archiva lo suyo y no da explicaciones; aquí el motivo es obligatorio porque va en el correo que avisa. |
| Condición | `Condition` | Nuevo, como nuevo, buen estado, con detalles. El vendedor declara una de las cuatro; no hay una quinta. **Cuáles admite depende de la familia:** moda las cuatro, tecnología solo nueva (RN-064). |
| De segunda | `SecondHand` | Todo lo que no es nuevo, es decir las otras tres condiciones. Es el par comercial de "nuevo" en menús, filtros y textos. Solo aplica a moda. |
| Borrador | `Draft` | Publicación que el vendedor todavía está armando. No es visible para nadie más y se guarda incompleta. |
| Categoría | `Category` | Árbol de clasificación de la prenda. Cada categoría declara sus sistemas de talla admisibles y su grupo de medida. El árbol vive en `docs/producto/categorias.md`. |
| Familia | `CategoryFamily` | Primer nivel del árbol: parte superior, parte inferior, prenda entera, calzado, accesorios y tecnología. Son seis, y la sexta es la que decide que una categoría solo admita condición nueva (RN-064). No se publica en una familia, sino en una categoría suya. |
| Talla | `Size` | Talla declarada, en uno de los sistemas que admite la categoría. |
| Sistema de talla | `SizeSystem` | Qué escala se usa: por letra, numérica, en pulgadas de cintura, de calzado o única. Una categoría admite más de una y el vendedor elige. Lista cerrada en HU-007. |
| Medidas | `Measurements` | Medidas reales en centímetros tomadas por el vendedor. |
| Grupo de medida | `MeasurementGroup` | Qué medidas son obligatorias para una categoría. Una camisa, un zapato y un bolso no se miden igual, y sin agrupar habría que pedirlas todas o ninguna. Lista cerrada en HU-007. |
| Color | `Color` | Color declarado, de lista cerrada. Es filtro de catálogo, y en texto libre un filtro no filtra. |
| Motivo de rechazo de publicación | `ListingRejectionReason` | Lista cerrada con la que el moderador rechaza una publicación (RN-022). **No es** el `RejectionReason` de la verificación de vendedor: son dos listas distintas para dos decisiones distintas. |
| Acción de moderación | `ModerationAction` | Lista cerrada de lo que un moderador le hace a una publicación: aprobar, rechazar y retirar. Es lo que se anota en la bitácora, y RN-045 dice que ninguna se pierde. **No es** el estado de la publicación: el estado dice cómo quedó, la acción dice qué se hizo y por quién. |
| Toma | `Shot` | Una fotografía individual del conjunto de captura. |
| Tomas canónicas | `CanonicalShots` | Frontal, lateral derecha, lateral izquierda y posterior. |
| Asistente de captura | `CaptureWizard` | Pantalla que guía al vendedor por las ocho tomas de la secuencia, con silueta, cuadrícula y nivel, y recorta cada una a 3:4 antes de subirla. No aplica a tecnología sellada, que son cuatro tomas del empaque (RN-065). |
| Secuencia 360 | `SpinSequence` | Conjunto ordenado de tomas que alimenta el visor giratorio. |
| Visor 360 | `SpinViewer` | Componente que simula el giro de la prenda. |
| Favorito | `Favorite` | Publicación que alguien con cuenta guarda para volver a ella. Es **privado** y de quien lo marca: no lo ve el vendedor ni existe como cifra pública (RN-070). Se dice «favorito» y no «guardado» ni «lista de deseos», y el verbo en pantalla es guardar. |

Un producto es único: si el vendedor tiene dos iguales, son dos publicaciones.
Esto simplifica todo el modelo y es fiel al negocio de segunda mano.

**Producto, prenda y dispositivo no son sinónimos y el error de usarlos como si
lo fueran no es cosmético.** Casi todo el texto del sitio se escribió cuando
Sendik vendía solo moda y dice «prenda» donde hoy corresponde «producto». Una
regla que diga «prenda» cuando aplica a las dos familias deja la tecnología fuera
sin que nadie lo note, y un texto que diga «producto» donde la regla es solo de
moda promete algo que no se cumple. La palabra elegida es la que decide a qué se
aplica la frase.

## Transacción

| Español | Código | Definición |
|---|---|---|
| Respaldo | `Backing` | Que Sendik responde por la transacción: el pago no llega al vendedor hasta que el comprador confirma la entrega, y hay una ventana para reportar si lo recibido no es lo publicado. Es la promesa central del producto y la única palabra que la nombra: nunca "compra protegida", "garantía" ni "seguro". |
| Pedido | `Order` | Compra de uno o varios productos a un mismo vendedor. |
| Ítem de pedido | `OrderItem` | Un producto dentro del pedido. |
| Pago | `Payment` | Intento de cobro a través de la pasarela. |
| División del pago | `PaymentSplit` | Reparto entre vendedor y comisión de Sendik. |
| Retención del pago | `PaymentHold` | Estado en que el pago, ya recaudado por la pasarela, todavía no es del vendedor. Sendik no lo custodia: lo retiene la pasarela (RN-031). |
| Liberación del pago | `PaymentRelease` | Fin de la retención. Ocurre cuando el comprador confirma la entrega, o cuando vence la ventana de reclamo sin que confirme ni reporte, y habilita el desembolso (RN-034). |
| Comisión | `Commission` | 5% sobre el valor del producto, a cargo del vendedor. |
| Desembolso | `Payout` | Traslado del dinero al vendedor una vez liberado. |
| Envío | `Shipment` | Movimiento físico del producto. |
| Cotización de envío | `ShippingQuote` | Valor aproximado consultado a las transportadoras. |
| Guía | `TrackingCode` | Número de rastreo de la transportadora. |
| Entrega confirmada | `DeliveryConfirmed` | Hecho que habilita la liberación del pago. La confirma **el comprador**; la guía de la transportadora prueba que el paquete llegó, no que dentro venga lo publicado. |
| Ventana de reclamo | `ClaimWindow` | Los 3 días hábiles siguientes a la entrega en los que el comprador puede reportar (RN-051). Confirmar la entrega la cierra. |
| Producto no conforme | `NonConformingProduct` | El que no corresponde a lo publicado o llega con daño no declarado (RN-050). Que la talla no siente no lo es. |
| Reporte | `Report` | Aviso del comprador de que lo recibido no es conforme. Abre una disputa y suspende la liberación (RN-053). |
| Reintegro | `Refund` | Devolución al comprador del valor del producto y del envío que pagó. Sale de la retención, no del vendedor (RN-054). |
| Disputa | `Dispute` | Reclamación abierta por el comprador. Sus reglas existen desde Fase 1 (RN-050 a RN-058); el flujo que las ejecuta, en Fase 4. |

## Palabras que no se usan

| No decir | Decir |
|---|---|
| Artículo, item (en español) | Producto; prenda si es moda, dispositivo si es tecnología |
| Tienda, catálogo del vendedor | Perfil del vendedor |
| Usuario final, cliente | Comprador |
| Barato, ganga, oferta, descuento | Precio, valor |
| Stock, inventario | Publicación, prenda |
| Wallet, saldo, billetera | Desembolso |
| Escrow, custodia, fideicomiso | Respaldo, retención del pago |
| Garantía, seguro, para nombrar lo que ofrece Sendik | Respaldo. «Garantía del fabricante» sí se dice, referida a un dispositivo y a lo que responde su vendedor, nunca a lo que responde Sendik (RN-067) |
| Compra protegida, protección, te protegemos | Respaldo, compra con respaldo |
| Usado, de segunda mano, prelovado, vintage (como nombre de la categoría) | De segunda |
| Plata, billete | Dinero, pago, valor |

Las filas de dinero importan especialmente: el producto no promete precio bajo,
no custodia fondos de terceros y no vende un seguro. El lenguaje tiene que ser
coherente con eso, y las palabras de las dos últimas filas además tienen lectura
regulatoria en Colombia: describen figuras financieras que Sendik no ejerce
(RN-031).

## Verificación del vendedor

Los nombres del proceso de HU-002. Las listas cerradas con sus valores están en la
historia, en «Datos de referencia».

| Español | Código | Definición |
|---|---|---|
| Tipo de documento | `IdentityDocumentType` | Cédula de ciudadanía, cédula de extranjería o Permiso por Protección Temporal. Sin pasaporte. |
| Tipo de cuenta | `BankAccountType` | Ahorros, corriente o depósito electrónico. El tercero es el de las billeteras, que no son cuentas de ahorros aunque se usen igual, y la Fase 3 necesita distinguirlo para desembolsar. |
| Motivo de rechazo | `RejectionReason` | Lista cerrada. El moderador elige uno y puede añadir una nota, que viaja a la persona y nunca lleva información judicial ni de terceros. |
| Revocación | `Revocation` | Acto por el que un moderador le quita el sello a quien ya lo tenía. No es un rechazo tardío: el rechazo decide sobre una solicitud y la revocación sobre alguien que ya vende (RN-013, RN-069). |
| Motivo de revocación | `RevocationReason` | Lista cerrada de cinco valores (RN-069). **No es** el `RejectionReason` del rechazo, y no se mezclan. Sus valores describen lo que se comprobó y nunca lo califican como delito. |
| Entidad financiera | `Bank` | Banco o billetera donde el vendedor recibe. Vive en tabla con código estable, no en una enumeración del código. |

**«Depósito electrónico» y no «billetera»** en el tipo de cuenta, porque es lo que
la cuenta es; «billetera» describe el producto que la persona usa para llegar a
ella.

## Archivos e imágenes

Dos clases de archivo, y la distinción no es de implementación: es de garantías.
Una toma de producto se sirve a cualquiera que mire el catálogo; una cédula la ve
únicamente el proceso de verificación (RN-046). Mezclarlas en un solo concepto es
lo que hace posible publicar por error lo que nunca debía salir, así que en el
código son dos cosas con dos nombres.

| Español | Código | Definición |
|---|---|---|
| Clave de archivo | `FileKey` | El nombre con el que se guarda. Opaco y derivado de un identificador aleatorio, no ordenable por tiempo: es la excepción que ADR-0015 aparta, porque esta clave se publica. No se deriva del nombre original ni de nada de la persona. |
| Almacén público | `PublicFileStore` | Donde van las imágenes que el catálogo sirve a cualquiera: tomas de producto y foto de perfil. Cacheable, por CDN. |
| Almacén reservado | `RestrictedFileStore` | Donde van la cédula y la selfie. Privado, cifrado y con acceso auditado (RN-046, `docs/operacion/datos-personales.md`). Nunca se sirve por una dirección pública. |
| Imagen normalizada | `NormalizedImage` | Los bytes ya decodificados y vueltos a codificar, sin EXIF y con sus dimensiones conocidas. Es lo único que llega a guardarse: nunca se guarda lo que subió alguien tal como llegó. |

**Almacén y no «almacenamiento»** porque son dos, y hay que poder nombrar cada
uno. «Reservado» y no «privado» para no confundirlo con la visibilidad de una
publicación, que es otra cosa.

**El EXIF se quita siempre**, en las dos clases de archivo. Lleva las coordenadas
GPS de donde se tomó la foto: una toma de producto publicada con su EXIF dice
dónde vive el vendedor.

## Estados

**Cuenta:** `ACTIVE`, `BLOCKED`, `CLOSING`, `CLOSED`. Son los valores de
`UserStatus` y del `CHECK` de `users`. Estar sin verificar **no** es un estado:
es `email_verified_at` en nulo, porque la cuenta existe y se puede entrar en
ella, solo que sin poder hacer nada más que reenviar el correo (RN-002).
`CLOSING` está declarado y todavía no lo usa nadie: es el cierre que queda
pendiente por pedidos sin resolver (RN-009), y en Fase 1 no hay pedidos.

**Publicación:** `DRAFT`, `PENDING_REVIEW`, `PUBLISHED`, `REJECTED`, `PAUSED`,
`SOLD`, `ARCHIVED`. Sus transiciones son RN-061. `PAUSED` lo pone y lo quita el
vendedor sin pasar por moderación; `ARCHIVED` lo pueden poner el vendedor y el
moderador, y es terminal como `SOLD`.

**Pedido:** `CREATED`, `PAYMENT_PENDING`, `PAID`, `PREPARING`, `SHIPPED`,
`DELIVERED`, `RELEASED`, `CANCELLED`.

**Verificación del vendedor:** `NOT_STARTED`, `IN_PROGRESS`, `PENDING_REVIEW`,
`VERIFIED`, `REJECTED`, `REVOKED`.

`REVOKED` es distinto de `REJECTED` y no se pueden mezclar: `REJECTED` no pasó la
revisión, `REVOKED` la pasó y se le quitó después (RN-013). Con un solo estado para
las dos cosas no se puede responder «¿esta persona estuvo verificada alguna vez?»,
que es justo lo que hay que saber cuando sus publicaciones siguen visibles y no
puede crear nuevas.

Las transiciones válidas están en `reglas-negocio.md`. Ningún código puede
inventar un estado que no esté en estas listas.
