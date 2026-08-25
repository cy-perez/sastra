# Textos web

Fuente de la que salen las claves de Transloco del sitio informativo. No es un
documento para copiar y pegar en plantillas: **ningún texto visible se escribe en
el código** (`CLAUDE.md`). Cada bloque de aquí lleva su clave; el texto vive en
`frontend/src/i18n/es.json` y `en.json`, y la plantilla solo referencia la clave.

Historias que consume: HU-004 (portada) y HU-005 (las cuatro páginas
informativas). Reglas que respeta: las de `reglas-negocio.md`, citadas donde
importan.

## Cómo leer las marcas

| Marca | Significado |
|---|---|
| `clave.de.transloco` | La clave exacta bajo la que va el texto |
| `{{variable}}` | Sale de configuración, **nunca de la plantilla**: comisión, datos de empresa, plazos |
| **Fase 2** / **Fase 3** | El texto está redactado pero no se publica hasta esa fase, porque describe algo que no existe |
| ⚠️ | Falta un dato o una decisión. La lista completa está al final |

Todo bloque necesita su gemelo en inglés antes de publicarse. Una prueba compara
los dos árboles y falla si alguna clave existe en uno y no en el otro (HU-004).

---

## Decisiones de voz

**Mensaje central.** Sendik reúne la moda nueva y de segunda que hoy se vende
dispersa en redes, con vendedores verificados, publicaciones revisadas y un pago
que no llega al vendedor hasta que el comprador confirma la entrega.

**Promesa.** Seguridad, no precio bajo (`vision.md`). Nada comunica ganga,
liquidación ni descuento.

**Pronombre.** Tú, en todo el sitio, botones y mensajes de error incluidos.

**Voz.** Cercana, sobria, sencilla. Sin signos de exclamación, sin euforia.

**Palabra clave: respaldo.** Es el término del glosario (`Backing`) y es el que
se repite. **No** se dice "compra protegida", "protección", "te protegemos",
"garantía", "seguro", "custodia" ni "escrow": las cuatro últimas describen
figuras financieras que Sendik no ejerce y tienen lectura regulatoria en Colombia
(RN-031). Tampoco se dice "plata": el registro coloquial choca con una promesa
de seguridad.

**Lo que Sendik nunca dice.** "La mejor plataforma", "tu aliado en moda",
"transforma tu clóset".

**Con quién se compara.** El competidor real no es Mercado Libre: es la venta por
Instagram, los grupos de Facebook y los chats de WhatsApp, donde hoy ocurre la
mayor parte de la moda de segunda en Colombia y donde no hay verificación, ni
revisión de lo que se publica, ni retención del pago. Los diferenciadores están
escritos contra ese punto de comparación.

**Frase que no puede escribirse nunca:** "Sendik guarda tu dinero". Sendik no lo
guarda. Lo recauda y lo retiene **la pasarela** (RN-031, RN-033). La forma
correcta es "el pago queda retenido" o "el pago no llega al vendedor hasta que
confirmas".

---

## Mapa del sitio en Fase 1

| # | Ruta | Para quién | A qué pregunta responde |
|---|---|---|---|
| 1 | `/` | Vendedor, sobre todo | ¿Qué es esto y por qué publico aquí? |
| 2 | `/como-funciona` | Ambos, en dos recorridos | ¿Cómo funciona exactamente, de cada lado? |
| 3 | `/sobre-sendik` | Ambos | ¿Quién está detrás? |
| 4 | `/preguntas-frecuentes` | Ambos | ¿Y lo que aún me preocupa? |
| 5 | `/contacto` | Ambos | ¿Cómo los ubico y cómo ejerzo mis derechos? |

Más los tres documentos legales, que tienen su propio mecanismo versionado
(`docs/operacion/textos-legales.md`).

**Navegación principal** (`layout.nav.*`): Cómo funciona · Sobre Sendik ·
Preguntas frecuentes · Contacto. A la derecha, Entrar y Crear cuenta.

Ningún enlace del encabezado lleva a catálogo, categorías ni búsqueda: no
existen (HU-005, criterio 25). No hay menú de Nuevo / De segunda / categorías
hasta Fase 2, y el árbol de categorías todavía no está decidido.

**A quién le habla la portada.** Al vendedor. El sitio no arranca sin inventario
(`vision.md`), y un catálogo vacío quema la primera visita. El comprador tiene su
recorrido completo en `/como-funciona`, y la portada lo convence con las tres
tarjetas de confianza. Es la decisión contraria a la de un marketplace maduro y
se revisa cuando la oferta supere a la demanda.

---

# 1. Portada

`meta.home.title` · `meta.home.description`

**Título:** Sendik
**Descripción:** Compra y vende de forma ágil y segura.

La descripción ya no arrastra el descriptor viejo de marca: adopta el eslogan
aprobado (ADR-0022), que no nombra la categoría y por eso no se queda corto ahora
que el catálogo incluye tecnología.

⚠️ El **título** sigue siendo de marcador de posición. Propuesta, dentro de los
límites de buscador: `Vende moda nueva y de segunda con respaldo | Sendik`. No se
cambia todavía porque nombra solo una de las dos categorías.

## 1.1 Hero — `home.hero.*`

Texto vigente, ya implementado. Cualquier cambio aquí reabre HU-004.

| Clave | Texto |
|---|---|
| `home.hero.title` | Compra y vende de forma ágil y segura |
| `home.hero.body` | El pago queda retenido hasta que confirmas que la prenda llegó como la viste. |
| `home.hero.cta` | Crear cuenta |
| `home.hero.note` | Publicar es gratis. Solo cobramos cuando vendes. |

El botón dice **crear cuenta**, no publicar, porque publicar no existe hasta
Fase 2 y un botón nombra lo que ocurre al pulsarlo (HU-004, criterio 3). Es la
única llamada a la acción de la pantalla, rellena en tinta. El porcentaje no se
escribe: `home.hero.note` lo omite a propósito (RN-026, HU-004 criterio 4).

*Nota de diseño:* la primera pantalla en móvil contiene titular, apoyo y botón.

## 1.2 Cómo funciona en tres pasos — `home.steps.*`

Texto vigente, ya implementado.

| Clave | Título | Texto |
|---|---|---|
| `home.steps.publish` | Publicas tu prenda | Fotos, talla y medidas reales. Revisamos cada publicación antes de que aparezca. |
| `home.steps.sell` | Alguien la compra | El comprador paga y el pago queda retenido mientras la prenda viaja. |
| `home.steps.charge` | Cobras al confirmarse la entrega | Cuando el comprador confirma que recibió la prenda, se libera tu pago. |

Encabezado del bloque: `home.steps.title` → **Cómo funciona**.
Enlace de cierre a `/como-funciona`; no se renderiza mientras la ruta no exista.

## 1.3 Tarjetas de confianza — `home.trust.*`

Texto vigente, ya implementado. Exactamente tres, y cada una apunta a una regla.

| Clave | Título | Texto | Regla |
|---|---|---|---|
| `home.trust.hold` | El pago queda retenido | El dinero no llega al vendedor hasta que confirmas que recibiste la prenda. | RN-034 |
| `home.trust.verified` | Vendedores verificados | Validamos identidad y cuenta bancaria antes de la primera publicación. | RN-011 |
| `home.trust.moderated` | Publicaciones revisadas | Cada publicación pasa por revisión antes de aparecer. Sin excepciones. | RN-015 |

Encabezado: `home.trust.title` → **Por qué es seguro**.

Ninguna tarjeta enuncia plazos: ni los días de la ventana de reclamo, ni tiempos
de entrega, ni de desembolso (HU-004, criterio 9). Un plazo suelto en portada se
lee como promesa y se vuelve exigible sin el contexto que lo condiciona; su sitio
es `/como-funciona` y los términos.

## 1.4 Pie de página — `layout.footer.*`

| Clave | Texto |
|---|---|
| `layout.footer.tagline` | Compra y vende de forma ágil y segura. |
| `layout.footer.company.taxId` | NIT — es la única etiqueta traducible; el número, la razón social y la dirección son valores de `AppConfig` y se pintan tal cual |
| `layout.footer.contactLabel` | Contacto |
| `layout.footer.legalLabel` | Documentos legales |

Enlaces: Cómo funciona · Sobre Sendik · Preguntas frecuentes · Contacto ·
Términos y condiciones · Política de tratamiento de datos · Política de cookies.

Razón social, NIT y dirección salen de configuración y se omiten si faltan;
nunca se escriben en la plantilla (HU-004, criterios 11 y 12).

© `{{year}}` Sendik.

**Fase 3.** Cuando existan los pagos y los envíos, el pie suma dos filas de
logos: medios de pago (PSE · Nequi · Bancolombia a la mano · Tarjetas · Addi) y
transportadoras (Envía · Coordinadora · Interrapidísimo). En Colombia esos logos
hacen más por la confianza que cualquier sello de "sitio seguro". Hoy no van:
anunciarían una funcionalidad que no existe.

---

# 2. Cómo funciona — `/como-funciona`

`meta.howItWorks.title` → **Cómo funciona**
`meta.howItWorks.description` → Cómo se compra y cómo se vende en Sendik:
verificación del vendedor, publicaciones revisadas, pago retenido hasta que el
comprador confirma la entrega y qué hacer si lo recibido no corresponde.

**H1** (`howItWorks.title`): Cómo funciona Sendik

**Entrada** (`howItWorks.intro`): Comprar o vender moda por internet en Colombia
suele significar transferirle a un desconocido y esperar, o despachar y esperar.
Sendik se pone en medio con reglas iguales para los dos lados.

Los dos recorridos van uno tras otro, ambos visibles y rotulados. Ninguno queda
detrás de una pestaña ni de un acordeón que un buscador no pueda seguir
(HU-005, criterio 5).

## 2.1 Si vas a comprar — `howItWorks.buyer.*`

**H2:** Si vas a comprar

| # | Clave | Título | Texto |
|---|---|---|---|
| 1 | `.browse` | Eliges una prenda | Cada publicación pasó por revisión antes de aparecer y muestra la prenda desde ocho ángulos, con su talla, sus medidas reales en centímetros y su condición declarada. Una prenda, una publicación: lo que ves es la pieza exacta que recibes. |
| 2 | `.pay` | Pagas producto más envío | El envío se cotiza con las transportadoras y el valor es aproximado; lo ves antes de confirmar. La comisión de Sendik no se te suma: la asume el vendedor. |
| 3 | `.hold` | El pago queda retenido | Tu pago lo recauda y lo retiene la pasarela. No llega al vendedor mientras la prenda viaja. |
| 4 | `.confirm` | Confirmas y se libera | Cuando recibes la prenda y confirmas que corresponde a lo publicado, el pago se libera. Si no confirmas ni reportas nada dentro de la ventana de reclamo, se da por confirmada. |

Reglas: RN-016 a RN-021 (paso 1), RN-027 y RN-038 (paso 2), RN-031 y RN-033
(paso 3), RN-034 y RN-052 (paso 4).

El paso 2 dice **aproximado** porque RN-038 obliga a rotularlo así, y no anuncia
plazo de entrega: no hay regla que lo respalde. El paso 3 dice "la pasarela", no
"Sendik".

### Si lo que recibes no es lo publicado — `howItWorks.buyer.claim.*`

**H3:** Si lo que recibes no es lo publicado

**Texto:** Tienes `{{claimWindowDays}}` días hábiles desde la entrega para
reportarlo desde tu pedido, con fotos de lo que recibiste. Mientras el reporte
esté abierto el pago sigue retenido, así que el reintegro sale de ese dinero y no
depende de que el vendedor colabore. Si el reporte se acepta, se te devuelve el
valor de la prenda y el envío que pagaste, y el flete de regreso lo asume el
vendedor.

**Ojo:** confirmar la entrega cierra la ventana. Si algo no cuadra, repórtalo
**antes** de confirmar.

**H3:** Qué no entra en el reporte

**Texto:** Que la talla no siente como esperabas o que el color se vea distinto
en pantalla no es un producto no conforme, siempre que la prenda corresponda a lo
publicado. Por eso cada publicación lleva medidas reales en centímetros: mídete
una prenda que te quede bien y compárala antes de comprar.

Aparte de esto existe el derecho de retracto que fija la ley colombiana, con sus
propios plazos y excepciones. Está en los
[Términos y condiciones](/terminos-y-condiciones).

Reglas: RN-050 a RN-058. La cifra de la ventana sale de configuración. Los plazos
del retracto **no se escriben aquí**: viven en el documento legal versionado y
esta página enlaza (RN-057).

*Nota de redacción:* decir qué **no** cubre el respaldo es lo que hace creíble lo
que sí cubre. Una garantía que suena ilimitada no se la cree nadie y genera
reclamos que no se pueden atender.

## 2.2 Si vas a vender — `howItWorks.seller.*`

**H2:** Si vas a vender

| # | Clave | Título | Texto |
|---|---|---|---|
| 1 | `.verify` | Te verificas | Validas tu documento de identidad, envías una selfie y registras una cuenta bancaria a tu nombre. Es gratis y se hace una sola vez. Solo personas naturales mayores de edad pueden vender. |
| 2 | `.publish` | Publicas | El asistente de captura te guía para tomar la prenda desde ocho ángulos y recorta todo a la misma proporción, así que tus fotos se ven como las del resto del catálogo. Declaras talla, medidas en centímetros y condición. Publicar no cuesta. |
| 3 | `.review` | Revisamos | Cada publicación pasa por revisión antes de aparecer. Si algo falta, te decimos qué corregir y la reenvías. |
| 4 | `.ship` | Despachas | Cuando alguien compra, el pago ya está retenido. Despachas con la transportadora y registras la guía. |
| 5 | `.charge` | Cobras | Recibes el valor de la prenda menos la comisión del `{{commissionRate}}`, en la cuenta bancaria que verificaste. Se libera cuando el comprador confirma la entrega o cuando vence la ventana de reclamo. |

Reglas: RN-008 y RN-010 a RN-012 (paso 1), RN-016 a RN-021 y RN-025 (paso 2),
RN-015 y RN-022 (paso 3), RN-034 y RN-052 (paso 5).

El paso 5 dice "en la cuenta bancaria que verificaste", no "a tu saldo": Sendik
no tiene billetera ni saldo interno (glosario). Y **no dice en cuántos días
hábiles llega**: ese plazo se decide en Fase 3 y hasta entonces no se escribe en
ninguna parte (HU-005, criterio 17).

### Qué asumes como vendedor — `howItWorks.seller.duties.*`

**H3:** Lo que asumes

- Describir la condición real, incluidas las manchas, el desgaste y las costuras
  sueltas. Las medidas en centímetros son obligatorias: son la causa número uno
  de reclamo en moda de segunda.
- Si la prenda no corresponde a lo publicado o llega con un daño que no
  declaraste, asumes el flete de regreso y el comprador recibe su reintegro.
- No se publican réplicas ni falsificaciones, ropa interior usada, productos que
  no sean moda ni tecnología, ni prendas con daño no declarado.
- La tecnología se vende **solo nueva**. Lo usado se vende únicamente en moda.

Reglas: RN-021, RN-024, RN-055, RN-064.

*Nota de redacción:* esta sección parece un obstáculo y es lo contrario: atrae al
vendedor serio y ahuyenta al que genera los reclamos. Un marketplace de segunda
se muere por los malos vendedores, no por los pocos vendedores.

### Por qué aquí y no en un grupo de Facebook — `howItWorks.seller.why.*`

- **El comprador ya pagó.** No despachas contra una promesa de transferencia.
- **No cuesta publicar.** Puedes tener prendas publicadas sin vender y no pagas
  nada. Solo se cobra cuando vendes.
- **Tus fotos se ven bien.** El asistente de captura fuerza el encuadre y la
  proporción, y con esas mismas tomas se arma la vista giratoria.
- **Cobras a tu cuenta.** El desembolso llega a la cuenta bancaria que
  verificaste.

**Cierre de página** (`howItWorks.cta`): botón **Crear cuenta**. Es la única
llamada a la acción de la página, rellena en tinta (HU-005, Diseño).

---

# 3. Sobre Sendik — `/sobre-sendik`

`meta.about.title` → **Qué es Sendik**
`meta.about.description` → Sendik reúne la moda nueva y de segunda que hoy se
vende dispersa en redes, con vendedores verificados, publicaciones revisadas y el
pago retenido hasta que el comprador confirma la entrega.

**H1** (`about.title`): Por qué existe Sendik

**Texto** (`about.body`):

En Colombia se compra y se vende muchísima ropa de segunda, pero ocurre en grupos
de Facebook, en historias de Instagram y en chats de WhatsApp. El comprador
transfiere y espera. El vendedor despacha y espera. Los dos asumen un riesgo que
nadie está cubriendo.

Sendik reúne esa oferta dispersa en un solo catálogo y pone cuatro cosas que en
un chat no existen: vendedores verificados con documento de identidad, cada
publicación revisada antes de aparecer, fotos que muestran la prenda desde ocho
ángulos, y un pago que no llega al vendedor hasta que el comprador confirma que
recibió lo que se publicó.

No fabricamos ropa ni la revendemos, y no tocamos el dinero: lo recauda y lo
retiene la pasarela de pagos. Somos el lugar donde una persona le compra a otra
con reglas claras para las dos.

**Datos de empresa** (`about.company.*`): razón social `{{companyName}}`, NIT
`{{companyTaxId}}`, dirección `{{companyAddress}}`. De configuración; se omiten
si faltan (HU-005, criterio 10).

⚠️ **Falta lo que hace creíble esta página: quién está detrás.** Sendik es una
marca nueva sin trayectoria, y en ese caso la confianza viene de las personas.
Dos o tres líneas con nombre, ciudad y por qué se montó valen más que cualquier
párrafo de misión.

*Nota de diseño:* nada de fotos de banco de imágenes con modelos sonriendo. Foto
real de quien está detrás, o ninguna foto.

*Nota de redacción:* esta página **no lleva** misión, visión ni valores. No los
lee nadie y en una marca nueva suenan a relleno. Tampoco comunica ganga,
liquidación ni precio bajo (HU-005, criterio 11).

---

# 4. Preguntas frecuentes — `/preguntas-frecuentes`

`meta.faq.title` → **Preguntas frecuentes**
`meta.faq.description` → Cómo comprar y vender en Sendik: verificación de
vendedores, publicaciones revisadas, pago retenido, comisión, medios de pago y
qué hacer si lo recibido no corresponde.

**H1** (`faq.title`): Preguntas frecuentes

Cada pregunta es un `details`/`summary` con identificador propio, para poder
enviar una respuesta por enlace. El texto está en el documento aunque esté
plegado (HU-005, criterios 13, 14 y 18).

## 4.1 Si vas a comprar — `faq.buyer.*`

**¿Cómo sé que el vendedor es real?** (`.real`)
Todos los vendedores validan su documento de identidad, una selfie y una cuenta
bancaria a su nombre antes de poder publicar. No se puede vender de forma
anónima. (RN-011, RN-012)

**¿A quién le pago?** (`.whoGetsPaid`)
El pago lo recauda y lo retiene la pasarela, no Sendik. El vendedor no lo recibe
mientras la prenda viaja. (RN-031, RN-033)

**¿Cuándo se le paga al vendedor?** (`.release`)
Cuando confirmas que recibiste la prenda y corresponde a lo publicado. Si no
confirmas ni reportas nada dentro de la ventana de reclamo, se da por confirmada
y el pago se libera. (RN-034, RN-052)

**¿Y si me llega algo distinto a lo publicado?** (`.claim`)
Mientras no confirmes la entrega el pago sigue retenido. Tienes
`{{claimWindowDays}}` días hábiles desde la entrega para reportarlo desde tu
pedido, con fotos de lo que recibiste. El reintegro sale de ese dinero y no
depende de que el vendedor colabore: si el reporte se acepta, recibes el valor
de la prenda y el envío que pagaste, y el flete de regreso lo asume el vendedor.
Confirmar la entrega cierra la ventana: si algo no cuadra, repórtalo antes de
confirmar. (RN-050 a RN-056)

El orden de los cuatro hechos es el que fija el criterio 16 de HU-005 y no se
reordena al corregir la redacción: primero que el dinero no se ha ido a ninguna
parte, después el plazo. Hay una prueba que falla si se altera.

**¿La talla no me quedó, puedo devolverla?** (`.fit`)
Que la talla no siente o que el color se vea distinto en pantalla no es un
producto no conforme. Por eso cada publicación lleva medidas reales en
centímetros. Aparte existe el derecho de retracto que fija la ley, con sus plazos
y condiciones, en los [Términos y condiciones](/terminos-y-condiciones).
(RN-050, RN-057)

**¿Cuánto cuesta el envío?** (`.shippingCost`)
Depende del destino, del peso y del tamaño. Se cotiza con las transportadoras y
lo ves antes de pagar. Es un valor aproximado. El envío lo paga el comprador y la
comisión de Sendik no se te suma. (RN-027, RN-038, RN-039)

**¿Cómo puedo pagar?** (`.payment`)
Con PSE, Nequi, Bancolombia a la mano y tarjeta débito o crédito a través de
Wompi, o a cuotas con Addi. No hay pago contraentrega. (RN-032)

**¿Puedo comprarle a dos vendedores a la vez?** (`.multiSeller`)
Sí, pero se generan dos pedidos, uno por vendedor, cada uno con su envío y su
propio recorrido. (RN-042)

## 4.2 Si vas a vender — `faq.seller.*`

**¿Cuánto cobran?** (`.commission`)
El `{{commissionRate}}` sobre el valor de la prenda, a cargo del vendedor y solo
cuando vendes. El envío no entra en la base de cálculo. Publicar es gratis.
(RN-026)

**¿Cuándo me pagan?** (`.payout`)
El pago se libera cuando el comprador confirma la entrega, o cuando vence la
ventana de reclamo sin que confirme ni reporte. Se desembolsa a la cuenta
bancaria que verificaste, no a un saldo dentro de la plataforma. (RN-034,
RN-052)

**¿Qué necesito para vender?** (`.requirements`)
Tu documento de identidad, una selfie y una cuenta bancaria a tu nombre. Solo
personas naturales mayores de edad, y la cuenta tiene que ser del titular del
documento. (RN-008, RN-010, RN-011, RN-012)

**¿Qué fotos me piden?** (`.photos`)
Ocho tomas de la prenda a 45 grados, que el asistente de captura te guía a tomar
y recorta a la misma proporción. De ahí salen las cuatro vistas obligatorias
—frente, los dos lados y espalda— y la vista giratoria. (RN-016 a RN-019)

**¿Puedo vender productos nuevos?** (`.new`)
Sí. Sendik tiene catálogo de nuevo y de segunda, y cada publicación indica su
condición. (RN-024 para lo que no se puede publicar)

**¿Puedo vender tecnología?** (`.tech`)
Sí, y solo nueva: celulares, computadores, televisores y demás se publican sin
uso. Lo de segunda se vende únicamente en moda. Si el producto está sellado, se
publica con cuatro fotos del empaque y puedes añadir imágenes de referencia del
fabricante, que salen siempre marcadas como tales. (RN-064, RN-065, RN-066)

**¿Qué no puedo publicar?** (`.forbidden`)
Réplicas o falsificaciones, ropa interior usada, tecnología usada, productos que
no sean moda ni tecnología, y prendas con daño que no declares. (RN-024, RN-064)

**¿Por qué revisan mi publicación?** (`.moderation`)
Porque un catálogo limpio es lo que hace que valga la pena comprar aquí. Toda
publicación pasa por revisión, sin excepciones. Si se rechaza, se te dice el
motivo y qué corregir, y la puedes reenviar. (RN-015, RN-022)

**¿Puedo publicar dos prendas iguales en una sola publicación?** (`.stock`)
No. Una prenda, una publicación. Si tienes dos iguales, son dos publicaciones.
(RN-025)

**¿Qué pasa si el comprador reclama?** (`.dispute`)
Si la prenda no corresponde a lo publicado o llegó con un daño que no declaraste,
asumes el flete de regreso y el comprador recibe su reintegro. Describir bien la
condición real es la mejor forma de que no ocurra. (RN-055)

---

# 5. Contacto — `/contacto`

`meta.contact.title` → **Contacto**
`meta.contact.description` → Cómo comunicarte con Sendik y cómo ejercer tus
derechos sobre tus datos personales.

**H1** (`contact.title`): Escríbenos

**Texto** (`contact.intro`): Si tienes un problema con un pedido, escríbenos
desde el correo con el que compraste y con el número de pedido a la mano. Así
respondemos más rápido.

**Correo** (`contact.email`): `{{supportEmail}}`, como enlace `mailto:` y como
texto legible. Si no está configurado, la sección no se pinta y el servidor lo
registra como aviso: sin ese canal se incumple una obligación legal (HU-005,
criterio 19 y casos borde).

**Si ya tienes cuenta** (`contact.account`): Tu nombre, ciudad, teléfono y correo
los cambias tú desde [Mi cuenta](/mi-cuenta), sin escribirnos.

**Tus datos personales** (`contact.rights`):
`.channel`: Por este mismo canal ejerces tus derechos a conocer, actualizar,
rectificar y suprimir tus datos, y a revocar la autorización que nos diste.
`.deadlines`: Respondemos una consulta en diez días hábiles y un reclamo en
quince, prorrogables una vez.
Los detalles están en la
[política de tratamiento de datos](/politica-de-tratamiento-de-datos).

Van en dos claves y no en una porque la primera frase afirma que hay un canal.
Sin `SUPPORT_EMAIL` configurado esa sección no se pinta, y la frase quedaba
señalando a un canal inexistente: se omite. Los plazos y el enlace a la política
se enuncian siempre.

Fuente: `docs/operacion/datos-personales.md`, sección Derechos del titular.

**Canales adicionales** (`contact.channels.*`): solo se muestran si están
configurados; si no, la sección no aparece. Ninguno se escribe en la plantilla
(HU-005, criterio 22).

⚠️ Hoy no existe ninguna variable de configuración para WhatsApp, horario de
atención ni redes. Si se quieren mostrar, hay que agregarlas a
`docs/operacion/configuracion.md`. No se inventan aquí.

## Formulario de contacto — fuera de alcance

HU-005 lo excluye: implica endpoint, antispam y aviso de privacidad propio. El
texto queda escrito para cuando se decida, y hasta entonces no se implementa.

- Campos: Nombre · Correo · Número de pedido *(opcional)* · Mensaje
- Junto al botón: ⚠️ *sin plazo de respuesta hasta que exista uno comprometido*
- Botón: Enviar mi mensaje
- Casilla de autorización, **sin premarcar** y obligatoria:

> ☐ Autorizo a `{{companyName}}`, NIT `{{companyTaxId}}`, a tratar mis datos
> personales para responder mi solicitud, conforme a su
> [política de tratamiento de datos](/politica-de-tratamiento-de-datos).

El enlace va **fuera** de la etiqueta y abre en pestaña nueva: dentro de la
etiqueta, pulsarlo marcaría la casilla además de abrir el documento, y se
aceptaría sin haber leído con un solo gesto (`datos-personales.md`). La política
tiene que estar publicada antes de activar el formulario, y la redacción la
ajusta un abogado.

---

# Microcopy transversal

## Botones

Un verbo por acción, en todo el sitio.

| Lugar | Texto | Fase |
|---|---|---|
| Hero de portada y cierre de páginas | Crear cuenta | 1 |
| Encabezado | Entrar | 1 |
| Vendedor, una vez exista publicar | Publicar mi primera prenda | 2 |
| Ficha de producto | Comprar | 3 |
| Carrito | Comprar y pagar | 3 |
| Cotizador de envío | Calcular mi envío | 3 |
| Pedido recibido | Confirmar que recibí la prenda | 3 |
| Pedido con problema | Reportar un problema | 4 |

Nunca: "Enviar", "Más información", "Haz clic aquí", "Adquirir", "Ordenar",
"Solicitar".

## Mensajes — Fase 1

**Búsqueda sin resultados** ⚠️ *no aplica hasta Fase 3*

**Error de campo** (`form.errors.*`): Falta tu correo para poder responderte.
Nunca "Campo inválido".

**Página 404** (`notFound.*`): Esta página no existe o la movimos. Vuelve al
inicio o escríbenos si buscabas algo puntual.

## Verificación de vendedor — Fase 2

El texto de HU-002. Las claves van bajo `sellerVerification.*`, y los mensajes de
error no están aquí: salen del código de error, en `errors.byCode.*`.

**Encabezado** (`sellerVerification.intro.*`):
`.title`: Verifícate para vender
`.body`: Necesitamos confirmar quién eres antes de que publiques. Son tres pasos y
puedes salir y volver cuando quieras: guardamos lo que ya hiciste.
`.reviewTime`: Revisamos tu solicitud en máximo `{{días}}` días hábiles.

**Los tres pasos** (`sellerVerification.steps.*`). Cada uno con su estado:

| Clave | Texto |
|---|---|
| `.document.title` | Tu documento de identidad |
| `.document.body` | Una foto del frente y otra del reverso. Que se lea todo, sin brillos ni dedos encima. |
| `.selfie.title` | Una foto de tu cara |
| `.selfie.body` | Se toma en el momento con tu cámara. No se puede subir desde la galería. |
| `.bank.title` | Dónde recibes tu dinero |
| `.bank.body` | La cuenta tiene que estar a tu nombre, el mismo del documento. |
| `.done` | Listo |
| `.pending` | Falta |

**Estados de la solicitud** (`sellerVerification.status.*`). La clave es el valor del
estado, en mayúsculas, porque la plantilla la compone con el dato que llega:
`.IN_PROGRESS`: Te falta algo por entregar.
`.PENDING_REVIEW`: Estamos revisando tu solicitud.
`.VERIFIED`: Ya eres vendedor verificado.
`.REJECTED`: No pudimos verificarte.
`.REVOKED`: Tu verificación se revocó.

**Enviar** (`sellerVerification.submit.*`):
`.action`: Enviar para revisión
`.blocked`: Completa los tres pasos para poder enviar.
`.attempts`: Te quedan `{{intentos}}` intentos.
`.exhausted`: Usaste tus tres intentos. Escríbenos y lo revisamos a mano.

**Rechazo** (`sellerVerification.rejected.*`):
`.reasonLabel`: Motivo
`.noteLabel`: Nota de quien revisó
`.retry`: Corregir y volver a enviar

Los motivos son lista cerrada y se traducen por su código
(`sellerVerification.reasons.*`): fotos ilegibles → «Las fotos no se pueden leer»;
documento vencido → «El documento está vencido»; titular distinto → «El titular de la
cuenta no coincide con tu documento»; documento ya verificado → «Ese documento ya está
verificado en otra cuenta»; requisitos → «No cumples los requisitos para vender».

**Lo que no se escribe aquí, y por qué.** No hay texto que prometa cuándo estará
aprobada más allá del plazo configurable, ni que anuncie qué se puede hacer al
verificarse: publicar llega con su propia historia y anunciarlo antes deja un enlace a
una ruta que no existe, que es lo que HU-004 y HU-005 prohíben.

## Bandeja del moderador — Fase 2

El texto de HU-006. Las claves van bajo `verificationReview.*`.

**Es la primera pantalla interna del sitio**, y eso cambia el tono: quien la usa
trabaja aquí, revisa muchas al día y no necesita que se le explique el producto. El
texto es corto y dice qué hace cada cosa. Nada de «¡Listo!» ni de acompañamiento.

**La bandeja** (`verificationReview.inbox.*`):
`.title`: Verificaciones pendientes
`.waitingSince`: Espera desde hace `{{tiempo}}`
`.attempt`: Intento `{{intento}}` de 3
`.noName`: Sin nombre todavía — cuando la solicitud aún no tiene documento entregado, que
es el único caso en que llega sin nombre.
`.empty.title`: No hay nada por revisar
`.empty.body`: Cuando alguien envíe su solicitud, aparece aquí.
`.error.title`: No pudimos cargar la bandeja
`.error.retry`: Reintentar

**El detalle** (`verificationReview.detail.*`):

| Clave | Texto |
|---|---|
| `.documentSection` | Documento de identidad |
| `.bankSection` | Cuenta bancaria |
| `.holder` | Titular |
| `.lastFour` | Termina en `{{dígitos}}` |
| `.documentType` | Tipo de documento |
| `.bank` | Entidad |
| `.accountType` | Tipo de cuenta |
| `.holderMismatch` | El titular de la cuenta no coincide con el del documento |
| `.back` | Volver a la bandeja |

`.holderMismatch` es el criterio 7 y **no es decorativo**: es la comprobación de
RN-012 dicha en palabras, para que no dependa de que alguien note un color.

**Las imágenes** (`verificationReview.images.*`):
`.front`: Frente del documento
`.back`: Reverso del documento
`.selfie`: Selfie
`.reveal`: Ver
`.missing`: Esta imagen no está disponible
`.notice`: Cada vez que abres una imagen queda registrado que la viste.

`.notice` se muestra siempre y no es una advertencia legal de relleno: la bitácora
existe (RN-046) y quien revisa tiene derecho a saber que también se le registra a él.

**Decidir** (`verificationReview.decision.*`):
`.approve`: Aprobar
`.reject`: Rechazar
`.reasonLabel`: Motivo del rechazo
`.reasonPlaceholder`: Elige un motivo
`.noteLabel`: Nota para la persona (opcional)
`.noteHint`: La lee quien envió la solicitud. No escribas información de terceros ni
datos de procesos judiciales.
`.confirmApprove`: ¿Aprobar esta verificación? La persona queda como vendedora
verificada y recibe un correo.
`.confirmReject`: ¿Rechazar esta verificación? La persona recibe un correo con el
motivo y podrá corregir si le quedan intentos.
`.confirm`: Confirmar
`.cancel`: Cancelar
`.approved`: Verificación aprobada
`.rejected`: Verificación rechazada
`.alreadyResolved`: Otra persona ya resolvió esta solicitud.
`.ownApplication`: Esta solicitud es tuya. La revisa otra persona.

`.ownApplication` es el criterio 12 y RN-060, dicho **antes** de que se pulse nada. El
servidor lo rechaza igual —esconder el botón no es la regla— pero enterarse después de
pulsar, con un correo ya prometido, no hace falta. Es también el único sitio donde la
pantalla sabe de quién es una solicitud, y solo eso: si es tuya o no.

`.noteHint` va donde se escribe la nota y no en una ayuda escondida: es la única
barrera que tiene esa regla, porque el campo es texto libre y ninguna validación
puede imponerla.

`.alreadyResolved` es el criterio 11. Se dice qué pasó, no «error inesperado».

**Los motivos de rechazo** se reutilizan de `sellerVerification.reasons.*` **solo si
sirven tal cual**, y no sirven: están escritos para quien recibe el rechazo, en
segunda persona. El moderador elige de una lista y necesita etiquetas cortas, así que
van aparte en `verificationReview.reasons.*`:

| Código | Texto |
|---|---|
| `ILLEGIBLE_PHOTOS` | Fotos ilegibles |
| `EXPIRED_DOCUMENT` | Documento vencido |
| `HOLDER_MISMATCH` | Titular no coincide |
| `DOCUMENT_ALREADY_VERIFIED` | Documento ya verificado en otra cuenta |
| `REQUIREMENTS_NOT_MET` | No cumple los requisitos |

**Sin acceso** (`verificationReview.forbidden.*`). Criterio 2: quien no es moderador
no puede enterarse de que esta pantalla existe, así que **no hay texto propio**. Se
reutiliza la página 404 (`notFound.*`), y esto es una decisión de seguridad, no un
ahorro de trabajo: un «no tienes permiso» confirma que hay algo detrás.

**Lo que no se escribe aquí, y por qué.** No hay texto de revocación: el endpoint
existe pero la acción quedó fuera de HU-006 porque no hay forma de llegar a una
verificación ya aprobada desde la interfaz. Tampoco hay texto para conceder el rol de
moderador: eso sigue siendo un `INSERT` a mano hasta el panel administrativo de la
Fase 4.

## Etiquetas de ficha de producto — Fase 2

- **Condición:** Nuevo · Como nuevo · Buen estado · Con detalles. Son las cuatro
  del glosario; no hay una quinta y no se inventa otra escala.
- **Vendido por:** `{{sellerName}}` · Vendedor verificado
- **Bajo el botón de compra:** Tu pago queda retenido hasta que confirmes que
  recibiste la prenda.

## Checkout — Fase 3

- **Paso de envío:** Cotizamos con Envía, Coordinadora e Interrapidísimo. Los
  valores son aproximados. Elige la que prefieras.
- **Paso de pago:** El pago lo recauda y lo retiene la pasarela. Se libera al
  vendedor cuando confirmes que recibiste la prenda.
- **Antes de confirmar:** Prenda + envío = total. Sin costos adicionales.
- **Botón final:** Comprar y pagar

## Textos alternativos de imagen

Describir la prenda real: *Bolso de cuero café con correa larga, vista frontal*.
Nunca *imagen1* ni cadenas de palabras clave.

---

# Pendientes reales

Los que estaban en la versión anterior de este documento y **ya están
resueltos** no aparecen aquí: la escala de condición existe (glosario), la lista
de productos prohibidos existe (RN-024), la ventana de reclamo y el reintegro
existen (RN-050 a RN-058), y quién confirma la entrega está decidido (RN-034: el
comprador).

## Tecnología en el catálogo — pendiente de redacción

El catálogo admite tecnología nueva desde el 24 de agosto de 2026 (RN-064 a
RN-067). Las reglas están escritas; **el texto del sitio, en su mayor parte, no**,
y hasta que lo esté no se toca `src/i18n`: inventar copia de cara al público en un
archivo de traducciones es exactamente lo que este documento existe para evitar.

Lo que ya se corrigió aquí es lo que había quedado **falso**: la lista de
productos prohibidos en «Qué asumes como vendedor» y en las preguntas frecuentes,
más la pregunta nueva sobre tecnología.

Lo que falta escribir:

- [x] ~~**El descriptor de marca dice «Compra y vende moda con respaldo»** y el
      catálogo ya no es solo moda.~~ **Resuelto por la marca Sendik** (ADR-0022):
      el eslogan es ahora **«Compra y vende de forma ágil y segura»**, que no
      nombra la categoría y por eso no vuelve a quedarse corto. Ya está en
      `layout.footer.tagline`, en el logo con eslogan y en el manual.
      **Sigue pendiente el titular del hero y la descripción para buscadores**,
      que son redacción y no marca: ver abajo.
- [x] ~~**El titular del hero y `meta.home.description` siguen diciendo «moda con
      respaldo».**~~ **Decidido el 25 de agosto de 2026:** ambos adoptan el
      eslogan tal cual, «Compra y vende de forma ágil y segura», y con ellos
      `meta.register.description` y `meta.login.description`, que arrastraban el
      mismo descriptor. Se descartó la variante de la maqueta del kit —«Compra y
      vende moda y tecnología de forma ágil y segura»—, que el propio kit marca
      como texto de muestra y que vuelve a nombrar las categorías. En inglés se
      reusa la fórmula del pie, `Buy and sell quickly and safely`, para que marca
      y copy no diverjan entre idiomas. Reabre y cierra HU-004.
- [ ] Los tres pasos de la portada y el recorrido de `/como-funciona` hablan solo
      de prendas, tallas y medidas. Hay que decidir si se generalizan o si se
      separan los dos recorridos.
- [ ] Los nombres visibles de las siete categorías de tecnología, en los dos
      idiomas.
- [ ] El rótulo de las imágenes de referencia en la ficha y en el carrusel, que
      RN-066 exige en los dos idiomas y que todavía no tiene texto.
- [ ] Cómo se enuncia la garantía del fabricante en la ficha sin usar la palabra
      Respaldo ni parecerse a ella (RN-067). **No se escribe hasta que pase
      revisión de abogado**, igual que los tres documentos legales.

## Decisiones de producto

- [ ] Especificaciones de tecnología: pulgadas, capacidad, memoria, modelo. Hoy
      no hay dónde guardarlas y el catálogo de tecnología no se puede filtrar por
      nada que le importe a quien compra un dispositivo.
- [x] Árbol de categorías del catálogo. **Decidido el 24 de agosto de 2026** en
      `docs/producto/categorias.md`: seis familias y treinta y una categorías, por
      tipo de producto. "Dama" y "Caballero" siguen sin ser categorías del
      proyecto. Faltan los nombres visibles en inglés.
- [ ] Plazo máximo de despacho del vendedor.
- [ ] Si hay límite de publicaciones activas por vendedor.
- [ ] Si se le exige al vendedor entregar la prenda limpia.
- [ ] Si existe chat comprador–vendedor. Hoy es Fase 4 (`alcance.md`), así que
      ningún texto puede decir "pregúntale al vendedor".
- [ ] Plazo de respuesta comprometido para el canal de contacto.

## Datos que faltan

- [ ] Días hábiles que tarda el desembolso en llegar a la cuenta del vendedor.
      Decidido en Fase 3 (`alcance.md`); hasta entonces ningún texto lo enuncia.
- [ ] Si la comisión incluye IVA y si la comisión de la pasarela se descuenta
      aparte. Si el vendedor recibe menos de lo que dice el texto, es publicidad
      engañosa.
- [ ] Si se anuncia algún plazo de entrega. Hoy no, y RN-038 obliga a rotular la
      cotización como aproximada.
- [ ] Quién está detrás de Sendik: nombre, ciudad y por qué se montó.
- [ ] Dirección y correo de soporte reales (`COMPANY_ADDRESS`, `SUPPORT_EMAIL`).
- [ ] Variables de configuración para canales adicionales, si se quieren mostrar.

## Revisión legal antes de abrir

- [ ] Términos y condiciones, con el derecho de retracto redactado según la ley.
- [ ] Política de tratamiento de datos publicada antes de activar cualquier
      formulario o registro.
- [ ] Política de devoluciones y reclamos, dentro de los términos
      (`docs/operacion/textos-legales.md`).
- [ ] **Qué responsabilidad tiene Sendik frente al comprador si el reintegro
      falla.** RN-054 lo mitiga —el dinero sigue retenido en la pasarela, así
      que no depende de que el vendedor colabore—, pero el Estatuto del
      Consumidor impone deberes propios a quien opera una plataforma de comercio
      electrónico. Es el punto que un abogado debe revisar primero.
- [ ] **La garantía legal de un producto de tecnología nuevo.** La Ley 1480 de
      2011 la impone sobre todo producto nuevo, y RN-067 dice que responde el
      vendedor y no Sendik. Que la regla lo diga no basta: hay que comprobar que
      es sostenible para una plataforma que además cobra comisión, y redactar
      cómo se enuncia sin rozar la palabra Respaldo. **Bloquea abrir la venta de
      tecnología**, no el resto del sitio.

## Traducción

- [ ] Versión en inglés de todo lo anterior. La estructura multi-idioma existe
      desde el día uno (`alcance.md`) y una prueba compara los dos árboles.

---

Recuerda: en Colombia lo que se anuncia es exigible. Cada plazo, cada porcentaje
y cada promesa de este documento se vuelve un compromiso el día que se publique.
Por eso las cifras salen de configuración y no de una plantilla, y por eso no se
escribe un plazo que no tenga una regla de negocio detrás.
