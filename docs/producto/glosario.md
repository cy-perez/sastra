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
| Comprador | `Buyer` | Rol del usuario cuando compra. |
| Vendedor | `Seller` | Rol del usuario cuando publica y vende. Persona natural. |
| Vendedor verificado | `VerifiedSeller` | Vendedor que superó identidad, selfie y validación bancaria. Muestra sello. |
| Moderador | `Moderator` | Persona de Sastra que aprueba o rechaza publicaciones. |

Un usuario tiene una sola cuenta. Ser vendedor no es otra cuenta: es un rol
adicional que se activa al completar la verificación.

## Catálogo

| Español | Código | Definición |
|---|---|---|
| Prenda, producto | `Product` | Artículo publicado. Unidad de venta. Existencia siempre 1. |
| Publicación | `Listing` | El producto junto a su estado de moderación y visibilidad. |
| Moderación | `Moderation` | Revisión de una publicación antes de que sea visible. Sin excepciones (RN-015). |
| Condición | `Condition` | Nuevo, como nuevo, buen estado, con detalles. El vendedor declara una de las cuatro; no hay una quinta. |
| De segunda | `SecondHand` | Todo lo que no es nuevo, es decir las otras tres condiciones. Es el par comercial de "nuevo" en menús, filtros y textos. |
| Categoría | `Category` | Árbol de clasificación de la prenda. |
| Talla | `Size` | Talla declarada, según el sistema de la categoría. |
| Medidas | `Measurements` | Medidas reales en centímetros tomadas por el vendedor. |
| Toma | `Shot` | Una fotografía individual del conjunto de captura. |
| Tomas canónicas | `CanonicalShots` | Frontal, lateral derecha, lateral izquierda y posterior. |
| Secuencia 360 | `SpinSequence` | Conjunto ordenado de tomas que alimenta el visor giratorio. |
| Visor 360 | `SpinViewer` | Componente que simula el giro de la prenda. |

Una prenda es única: si el vendedor tiene dos iguales, son dos publicaciones.
Esto simplifica todo el modelo y es fiel al negocio de segunda mano.

## Transacción

| Español | Código | Definición |
|---|---|---|
| Respaldo | `Backing` | Que Sastra responde por la transacción: el pago no llega al vendedor hasta que el comprador confirma la entrega, y hay una ventana para reportar si lo recibido no es lo publicado. Es la promesa central del producto y la única palabra que la nombra: nunca "compra protegida", "garantía" ni "seguro". |
| Pedido | `Order` | Compra de uno o varios productos a un mismo vendedor. |
| Ítem de pedido | `OrderItem` | Un producto dentro del pedido. |
| Pago | `Payment` | Intento de cobro a través de la pasarela. |
| División del pago | `PaymentSplit` | Reparto entre vendedor y comisión de Sastra. |
| Retención del pago | `PaymentHold` | Estado en que el pago, ya recaudado por la pasarela, todavía no es del vendedor. Sastra no lo custodia: lo retiene la pasarela (RN-031). |
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
| Artículo, item (en español) | Prenda o producto |
| Tienda, catálogo del vendedor | Perfil del vendedor |
| Usuario final, cliente | Comprador |
| Barato, ganga, oferta, descuento | Precio, valor |
| Stock, inventario | Publicación, prenda |
| Wallet, saldo, billetera | Desembolso |
| Escrow, custodia, fideicomiso | Respaldo, retención del pago |
| Garantía, seguro | Respaldo |
| Compra protegida, protección, te protegemos | Respaldo, compra con respaldo |
| Usado, de segunda mano, prelovado, vintage (como nombre de la categoría) | De segunda |
| Plata, billete | Dinero, pago, valor |

Las filas de dinero importan especialmente: el producto no promete precio bajo,
no custodia fondos de terceros y no vende un seguro. El lenguaje tiene que ser
coherente con eso, y las palabras de las dos últimas filas además tienen lectura
regulatoria en Colombia: describen figuras financieras que Sastra no ejerce
(RN-031).

## Estados

**Publicación:** `DRAFT`, `PENDING_REVIEW`, `PUBLISHED`, `REJECTED`, `PAUSED`,
`SOLD`, `ARCHIVED`.

**Pedido:** `CREATED`, `PAYMENT_PENDING`, `PAID`, `PREPARING`, `SHIPPED`,
`DELIVERED`, `RELEASED`, `CANCELLED`.

**Verificación del vendedor:** `NOT_STARTED`, `IN_PROGRESS`, `PENDING_REVIEW`,
`VERIFIED`, `REJECTED`.

Las transiciones válidas están en `reglas-negocio.md`. Ningún código puede
inventar un estado que no esté en estas listas.
