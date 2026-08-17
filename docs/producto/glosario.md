# Glosario

Este archivo manda sobre cómo se llaman las cosas. El término en inglés es el
que se usa en el código; el término en español es el que se usa al hablar, en la
documentación y en la interfaz. Si un concepto no está aquí, no existe todavía:
se agrega antes de escribirlo en código.

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
| Condición | `Condition` | Nuevo, como nuevo, buen estado, con detalles. |
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
| Pedido | `Order` | Compra de uno o varios productos a un mismo vendedor. |
| Ítem de pedido | `OrderItem` | Un producto dentro del pedido. |
| Pago | `Payment` | Intento de cobro a través de la pasarela. |
| División del pago | `PaymentSplit` | Reparto entre vendedor y comisión de Sastra. |
| Comisión | `Commission` | 5% sobre el valor del producto, a cargo del vendedor. |
| Desembolso | `Payout` | Traslado del dinero al vendedor una vez liberado. |
| Envío | `Shipment` | Movimiento físico del producto. |
| Cotización de envío | `ShippingQuote` | Valor aproximado consultado a las transportadoras. |
| Guía | `TrackingCode` | Número de rastreo de la transportadora. |
| Entrega confirmada | `DeliveryConfirmed` | Hecho que habilita la liberación del pago. |
| Disputa | `Dispute` | Reclamación abierta por el comprador. Fase 4. |

## Palabras que no se usan

| No decir | Decir |
|---|---|
| Artículo, item (en español) | Prenda o producto |
| Tienda, catálogo del vendedor | Perfil del vendedor |
| Usuario final, cliente | Comprador |
| Barato, ganga, oferta, descuento | Precio, valor |
| Stock, inventario | Publicación, prenda |
| Wallet, saldo, billetera | Desembolso |

Las tres últimas filas importan: el producto no promete precio bajo y no
custodia dinero. El lenguaje tiene que ser coherente con eso.

## Estados

**Publicación:** `DRAFT`, `PENDING_REVIEW`, `PUBLISHED`, `REJECTED`, `PAUSED`,
`SOLD`, `ARCHIVED`.

**Pedido:** `CREATED`, `PAYMENT_PENDING`, `PAID`, `PREPARING`, `SHIPPED`,
`DELIVERED`, `RELEASED`, `CANCELLED`.

**Verificación del vendedor:** `NOT_STARTED`, `IN_PROGRESS`, `PENDING_REVIEW`,
`VERIFIED`, `REJECTED`.

Las transiciones válidas están en `reglas-negocio.md`. Ningún código puede
inventar un estado que no esté en estas listas.
