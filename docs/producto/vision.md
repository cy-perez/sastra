# Visión de producto

## Qué es

Sastra es el mercado colombiano donde cualquier persona compra y vende moda,
nueva y usada, con la plataforma como respaldo de la transacción.

La promesa es **seguridad**, no precio bajo. Todo el sistema de marca sale de
ahí: el nombre suena a sastre, el símbolo es una puntada que une dos partes, y
el descriptor es "Compra y vende moda con respaldo". Nada en el producto debe
comunicar ganga.

## Para quién

**Vendedor.** Persona natural que quiere vender prendas que ya no usa o que
produce en pequeña escala. Vende desde el celular. Su fricción principal es
publicar bien: fotos, medidas, precio. Su miedo es que le paguen y no le
respondan, o entregar y no recibir.

**Comprador.** Persona que busca una prenda concreta o navega por gusto. Su
miedo es pagar y no recibir, o recibir algo distinto a la foto. Compra desde el
celular en la mayoría de los casos.

En ambos lados la unidad de confianza es la misma: la plataforma responde.

## Cómo gana dinero

Comisión del **5% sobre el valor del producto**, a cargo del vendedor, retenida
al momento del desembolso. El envío no hace parte de la base de cálculo.

Sastra no toca el dinero: la pasarela recauda y divide el pago. Ver `ADR-0005`.

## Qué lo diferencia

1. **Respaldo de la transacción.** El pago no llega al vendedor hasta que el
   comprador confirma la entrega, y si lo recibido no es lo publicado hay 3 días
   hábiles para reportarlo con el dinero todavía retenido (RN-050 a RN-058).
   Sastra no custodia ese dinero: lo retiene la pasarela (RN-031).
2. **Publicaciones moderadas.** Toda publicación se revisa antes de aparecer. Un
   catálogo limpio es el activo del sitio.
3. **Fotos consistentes y vista 360º.** El asistente de captura fuerza encuadre
   y proporción; con esas mismas tomas se arma una vista giratoria. Es lo que
   más se parece a ver la prenda en persona y es difícil de copiar.
4. **Vendedores verificados.** Identidad, selfie y cuenta bancaria validada.

## Qué NO es

- No es una red social. No hay muro, ni seguidores, ni comentarios públicos.
- No es un outlet ni un sitio de liquidación.
- No es dropshipping ni venta al por mayor.
- No es una billetera: Sastra no custodia dinero de terceros.
- No compite por precio. Compite por confianza.

## Señales de que va bien

En orden de importancia para las primeras fases:

1. Vendedores que completan el registro y la verificación.
2. Publicaciones que pasan moderación al primer intento.
3. Prendas publicadas por vendedor activo.
4. Compras completadas sin disputa.
5. Vendedores que vuelven a publicar después de su primera venta.

El sitio no arranca sin inventario, así que la acción principal de la portada es
**publicar una prenda**, y por eso lleva el único acento ocre de la pantalla.
Cuando la oferta supere a la demanda, se revisa esa decisión.

Esa decisión se ha cuestionado una vez, con el argumento de que la portada
debería hablarle solo al comprador y llevar al catálogo. Se mantiene: un
catálogo vacío quema la primera visita, que es la más cara de conseguir. El
comprador tiene su recorrido completo en `/como-funciona`, y la portada lo
convence con las tres tarjetas de confianza, no con una rejilla sin productos.
Mientras publicar no exista (Fase 2), el botón de la portada dice crear cuenta,
porque un botón nombra lo que ocurre al pulsarlo (HU-004).

## Alcance geográfico

Colombia, moneda COP, español por defecto e inglés disponible. La estructura
multi-idioma existe desde el día uno para no tener que reescribir después, no
porque haya un mercado extranjero en el corto plazo.
