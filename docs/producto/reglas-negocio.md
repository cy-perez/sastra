# Reglas de negocio

Fuente única de verdad para el comportamiento del sistema. Si una regla no está
aquí, no se implementa: se pregunta y se agrega.

Cada regla tiene identificador. Úsalo en el código y en las pruebas:
`// RN-012` y `deberia_cumplir_RN_012_...`.

## Cuentas y acceso

- **RN-001** Un correo electrónico corresponde a una sola cuenta.
- **RN-002** La cuenta no se activa hasta verificar el correo. Sin verificar solo
  se permite navegar; no publicar ni comprar.
- **RN-003** El enlace de verificación caduca a las 24 horas y es de un solo uso.
- **RN-004** El enlace de recuperación de contraseña caduca a los 30 minutos, es
  de un solo uso e invalida todas las sesiones abiertas al usarse.
- **RN-005** Contraseña de 10 caracteres como mínimo. Se rechaza si aparece en la
  lista de contraseñas filtradas conocidas. No se exigen símbolos obligatorios:
  la longitud protege más que la complejidad artificial.
- **RN-006** Cinco intentos fallidos de inicio de sesión bloquean el acceso a esa
  cuenta durante 15 minutos, contados desde el último intento.
- **RN-007** El token de acceso dura 15 minutos. El de refresco dura 30 días,
  rota en cada uso y se revoca toda la familia si se detecta reutilización. No
  cuenta como reutilización que un token vuelva a llegar dentro de los segundos
  siguientes a su rotación mientras el que salió de ella siga sin usarse: eso es
  una carrera entre dos pestañas del mismo navegador, que comparten la cookie. En
  ese caso se rechaza la petición pero no se revoca ni se avisa (ADR-0014).
- **RN-008** Solo mayores de 18 años. Se declara en el registro y se confirma en
  la verificación de identidad.
- **RN-009** El usuario puede cerrar su cuenta en cualquier momento. Si tiene
  pedidos en curso, el cierre queda pendiente hasta que se resuelvan.

## Vendedor

- **RN-010** Solo persona natural. Un documento de identidad, un vendedor.
- **RN-011** Para publicar hay que estar verificado: identidad, selfie y cuenta
  bancaria a nombre del mismo documento.
- **RN-012** La cuenta bancaria debe pertenecer al titular de la cédula
  registrada. Si no coincide, la verificación se rechaza.
- **RN-013** El sello de vendedor verificado se pierde si la verificación se
  revoca; sus publicaciones activas siguen visibles pero no puede crear nuevas.
- **RN-014** Un vendedor rechazado puede reintentar. Máximo tres intentos; el
  cuarto exige revisión manual.

## Publicación

- **RN-015** Toda publicación pasa por moderación antes de ser visible. Sin
  excepciones ni listas blancas.
- **RN-016** Se exigen cuatro tomas canónicas: frontal, lateral derecha, lateral
  izquierda y posterior. Sin las cuatro no se puede enviar a revisión.
- **RN-017** Para la secuencia 360 se capturan **ocho** tomas a 45 grados. Las
  cuatro canónicas son las de 0, 90, 180 y 270 grados: se extraen de la misma
  secuencia, no se toman aparte.
- **RN-018** Todas las tomas se recortan a proporción 3:4 en el cliente antes de
  subirlas, con el producto centrado. Es la proporción del catálogo y no es
  negociable: si cada foto llega con la suya, la rejilla se rompe.
- **RN-019** Resolución mínima de 900 x 1200 px por toma. Por debajo, el
  formulario no deja continuar.
- **RN-020** El precio se expresa en pesos colombianos, sin decimales, mínimo
  10.000 y máximo 20.000.000. Fuera de ese rango exige revisión manual.
- **RN-021** El vendedor declara condición, talla y medidas reales en
  centímetros. Las medidas son obligatorias: son la causa número uno de
  devolución en moda de segunda mano.
- **RN-022** Una publicación rechazada indica siempre el motivo y qué corregir.
  Se puede editar y reenviar a revisión.
- **RN-023** Una publicación vendida no se edita ni se reactiva.
- **RN-024** Publicaciones prohibidas: réplicas o falsificaciones, ropa interior
  usada, artículos que no sean moda o accesorios, prendas con daño no declarado.
- **RN-025** Existencia siempre igual a 1. Una prenda, una publicación.

## Precio y comisión

- **RN-026** La comisión es del **5% sobre el valor del producto**, a cargo del
  vendedor. El envío no entra en la base de cálculo.
- **RN-027** El comprador paga: valor del producto + valor del envío. La comisión
  no se le suma; se descuenta del desembolso al vendedor.
- **RN-028** El redondeo de la comisión es al peso más cercano, con la mitad
  hacia arriba. Se guarda el valor calculado, nunca se recalcula al mostrarlo.
- **RN-029** Todo cálculo de dinero se hace con decimales exactos, jamás con
  números de punto flotante.
- **RN-030** El precio se congela al crear el pedido. Un cambio posterior de
  precio en la publicación no afecta pedidos existentes.

## Pago

- **RN-031** El recaudo lo hace Wompi. Sastra no recibe ni custodia dinero de
  terceros.
- **RN-032** Medios habilitados: PSE, Nequi, tarjeta débito y crédito,
  Bancolombia a la mano y Addi. El pago contraentrega no está habilitado.
- **RN-033** La división del pago separa el valor del vendedor y la comisión de
  Sastra en la propia pasarela.
- **RN-034** El pago se libera al vendedor cuando la entrega se confirma. Los
  días exactos de retención se definen en Fase 3.
- **RN-035** La publicación se marca vendida cuando el pago queda aprobado, no
  cuando se inicia el intento.
- **RN-036** El estado del pago se confirma siempre contra la pasarela, nunca
  contra lo que diga el navegador del comprador.
- **RN-037** Todo evento recibido de la pasarela se verifica por firma y se
  procesa de forma idempotente: el mismo evento dos veces produce un solo efecto.

## Envío

- **RN-038** El cotizador consulta a Envía, Coordinadora e Interrapidísimo y
  muestra valores **aproximados**, siempre rotulados como tales.
- **RN-039** La cotización se calcula con el peso y las dimensiones declaradas
  por el vendedor, y el destino del comprador.
- **RN-040** Si una transportadora no responde, se muestran las demás. La
  cotización nunca bloquea la compra.
- **RN-041** La cotización se guarda con el pedido para poder auditar diferencias
  con el valor real.

## Pedido

- **RN-042** Un pedido corresponde a un solo vendedor. Comprar a dos vendedores
  genera dos pedidos.
- **RN-043** Un pedido sin pago aprobado en 60 minutos se cancela y la prenda
  vuelve a estar disponible.
- **RN-044** Transiciones válidas: `CREATED` a `PAYMENT_PENDING` a `PAID` a
  `PREPARING` a `SHIPPED` a `DELIVERED` a `RELEASED`. Se puede cancelar desde
  `CREATED` y `PAYMENT_PENDING`.
- **RN-045** Ningún estado retrocede. Toda transición queda registrada con
  fecha, actor y motivo.

## Datos personales

- **RN-046** La cédula, la selfie y la cuenta bancaria se guardan cifradas y solo
  las ve el proceso de verificación. Nunca salen en una respuesta de la API.
- **RN-047** El comprador ve del vendedor: nombre, ciudad, sello de verificado y
  reputación. Nada más.
- **RN-048** La dirección completa del comprador se revela al vendedor solo
  cuando el pago está aprobado.
- **RN-049** El usuario puede solicitar la eliminación de sus datos. Se conserva
  lo que la ley obligue a conservar por razones contables y fiscales, y se
  documenta qué es y por cuánto tiempo.
