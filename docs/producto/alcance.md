# Alcance por fases

Regla: no se implementa funcionalidad de una fase posterior, aunque el diseño ya
la contemple y aunque sea fácil. Lo que no está en la fase actual, se anota.

## Fase 1 — cimientos, cuentas y sitio informativo

**Cerrada el 21 de agosto de 2026.** Todo lo que sigue está implementado y probado.
Quedan dos cosas fuera de esa afirmación, las dos por decisión y las dos anotadas
donde corresponde:

- ~~**El primer despliegue está aplazado**~~ **Resuelto el 26 de agosto de 2026.**
  Se contrató `sendik.co` en GoDaddy y con eso se cerró la decisión que faltaba:
  el sitio se hospeda en Cloud Run junto al backend, y GoDaddy queda como
  registrador y DNS (ADR-0024). `dev` se despliega desde la integración continua,
  con las dos piezas, y sigue costando cero. **Producción sigue aplazada**, ahora
  por lo que de verdad la bloquea: los textos legales del punto siguiente y las
  piezas que cobran por hora encendida (`docs/operacion/entornos.md`).
- **Los tres textos legales siguen siendo `borrador-local`**, que es relleno sin
  valor legal (`docs/operacion/textos-legales.md`). El mecanismo completo está: las
  tres rutas, el resolutor que sirve el texto dentro del HTML renderizado, la
  versión que se guarda con el consentimiento y un aviso visible en la página
  mientras la versión sea un borrador. Lo que falta es el contenido, que no es
  trabajo de código y que **bloquea el lanzamiento, no el cierre de la fase**.

**Plataforma**
- Monorepo con backend y frontend, Gradle multi-módulo y Angular con SSR.
- Sistema de diseño implementado: tokens, componentes base, modo oscuro y
  selector de idioma.
- Controles y garantías de accesibilidad. La línea decía «controles de
  accesibilidad» sin definir cuáles, y una frase así no se puede dar por
  cumplida ni por incumplida. Son estos cinco, y están:
  enlace de salto al contenido, foco visible de 3px que nunca se elimina,
  conmutador de tema claro y oscuro, selector de idioma, y respeto de las
  preferencias del sistema (`prefers-reduced-motion` y el tema preferido). La
  lista completa y comprobable está en `docs/ui/accesibilidad.md`, y la audita
  `frontend/e2e/accesibilidad.spec.ts` con axe sobre WCAG 2.2 AA en los dos
  modos (ADR-0016). No entra ningún control de tamaño de texto: nadie lo ha
  decidido y no se inventa aquí.
- Internacionalización ES/EN funcionando con SSR.
- Canalización de integración continua: compilar, probar, analizar y desplegar.
  Los cuatro pasos existen: `.github/workflows/verificacion.yml` compila, prueba
  y analiza, y `despliegue.yml` publica **las dos piezas** —backend y frontend con
  renderizado en servidor— en `dev` con cada integración a `main`, y en `prod` con
  etiqueta y aprobación. El despliegue del frontend se escribió el 26 de agosto de
  2026, cuando contratar `sendik.co` cerró la decisión de hospedaje: va a Cloud Run
  junto al backend y GoDaddy queda como registrador y DNS (ADR-0024). **`prod`
  sigue aplazado por decisión**, no por falta de trabajo, y ahora por lo que de
  verdad lo bloquea: los textos legales y las piezas que cobran por hora encendida.
  El motivo está en `docs/operacion/entornos.md` y el procedimiento en
  `docs/operacion/despliegue.md`.
- Base de datos con migraciones y esquema inicial de usuarios.

**Cuentas**
- Registro de comprador y de vendedor con correo y contraseña.
- Verificación de correo.
- Inicio y cierre de sesión con JWT propio y refresco rotatorio.
- Recuperación de contraseña.
- Perfil básico editable.
- Cierre de cuenta y descarga de datos personales.

**Sitio informativo**
- Portada con la propuesta de valor y las tres tarjetas de confianza (HU-004).
- Cómo funciona, para comprador y para vendedor (HU-005).
- Sobre Sendik (HU-005).
- Preguntas frecuentes (HU-005).
- Contacto (HU-005).
- Términos y condiciones, política de tratamiento de datos, política de cookies.
  La política de devoluciones y retracto va dentro de los términos mientras no
  se decida separarla en documento propio (`docs/operacion/textos-legales.md`).

El texto de las cinco páginas vive en `docs/producto/textos-web.md`, que es la
fuente de la que salen las claves de Transloco.

**Fuera de esta fase:** publicar productos, catálogo, búsqueda, carrito, pagos,
envíos, verificación de identidad. El sitio informativo **describe** todo eso en
presente, porque es lo que convence a alguien de registrarse, pero no anuncia
fechas ni deja enlaces a rutas que no existen.

## Fase 2 — publicación y catálogo

Es la fase en curso desde el 21 de agosto de 2026.

- **Hecho.** Verificación de vendedor: identidad, selfie y cuenta bancaria
  (HU-002). Incluye los cinco endpoints de revisión del moderador; la bandeja
  con la que se usan es el punto de más abajo. Detrás de
  `FEATURE_SELLER_VERIFICATION`, hoy apagada.
- Asistente de captura de fotos con overlay, nivelador y recorte en cliente
  (HU-003). **Hecho el 28 de agosto de 2026**, en `/publicar/:id/capturar`: ocho pasos
  con nombre, silueta y cuadrícula, nivel de 5 grados, recorte a 3:4 en un worker y
  subida con progreso real. Es enteramente frontend: el backend ya tenía el endpoint
  desde HU-007.
- Visor 360º en la ficha de producto (HU-003). **Hecho el mismo día**, en `shared/ui`
  y sin ninguna librería. Se ofrece solo con la secuencia completa de ocho.
  La salvedad que quedaba —el recorrido de extremo a extremo con cámara simulada— **se cerró
  ese mismo 28 de agosto de 2026**, unas horas después: `e2e-completo/captura-y-visor.spec.ts`
  recorre las ocho tomas con la cámara falsa, aprueba, y comprueba sobre la ficha pública lo
  que hasta entonces no verificaba nada —el criterio 18 en el HTML que sale del servidor y el
  recorte a 3:4 sobre los píxeles que de verdad se guardaron—. Con eso la historia queda sin
  deuda.
- Publicación de producto con las cuatro tomas obligatorias y las intermedias, en
  las dos familias: moda —nueva y de segunda— y **tecnología, solo nueva**
  (RN-064). La tecnología se agregó al alcance el 24 de agosto de 2026 y entra en
  esta fase porque HU-007 todavía no tenía código: meterla entonces costaba menos
  que reabrir el catálogo después. Moverla a una fase posterior es cambiar esta
  línea. Trae también los endpoints de decisión del moderador sobre
  publicaciones, y con ella quedaron cerradas las reglas que faltaban: RN-061,
  RN-062 y RN-063.
  **HU-007 está hecha**: el backend el 25 de agosto de 2026 y la interfaz el 26.
  Están el dominio con su máquina de estados, los diecisiete casos de uso, la
  persistencia, el aviso por correo al vendedor, los diecisiete endpoints y las
  tres pantallas del vendedor —`/publicar`, `/publicar/:id` y
  `/mis-publicaciones`—, todo detrás de `FEATURE_PUBLISHING`, hoy apagada. Las
  cuatro decisiones de producto que la historia enumeraba se tomaron el 26 de
  agosto. **Lo que impide encender la bandera ya no es HU-007 sino HU-008:** sin
  bandeja del moderador, nada puede salir de `PENDING_REVIEW`.
- Panel de moderación y flujo de aprobación o rechazo con motivo. **Hecho a
  medias**: HU-006 entrega la bandeja de verificaciones de vendedor, y con ella
  `FEATURE_SELLER_VERIFICATION` ya se puede encender. La moderación de
  publicaciones —RN-015, la otra mitad de este punto— tiene sus reglas y sus
  endpoints de decisión en HU-007, y **HU-008 la cerró el 27 de agosto de 2026**: la
  cola, la bandeja y el detalle donde se decide. Con ella este punto queda completo.
  El recorrido de punta a punta del ciclo está escrito en `e2e-completo/`; las tres pruebas
  que nacieron en rojo se arreglaron el 27 de agosto de 2026 y el diagnóstico quedó en la
  historia. Fuera de las dos historias
  quedaban revocar un sello ya otorgado y bajar una publicación ya visible (RN-024):
  los endpoints existían, pero no había forma de llegar a esos identificadores desde la
  interfaz. **Las recogió HU-010, y las dos juntas**: esta línea decía que cada una
  esperaba su propia historia, y el 28 de agosto de 2026 se decidió lo contrario —el
  mismo rol, el mismo gesto de deshacer y la misma pantalla de confirmación—.
  **HU-010 quedó hecha el 1 de septiembre de 2026**: bajar desde la ficha pública,
  revocar desde el perfil del vendedor, la lectura nueva
  `GET /api/v1/verifications/by-seller/{sellerId}` para llegar de un vendedor a su
  verificación, y los dos ciclos recorridos por la interfaz en `e2e-completo/`.
  Escribirla dejó a la vista que la lista de motivos de revocación no existía: el
  endpoint reutilizaba la del rechazo, y sus cuatro valores hablan de la solicitud, no de
  un sello ya otorgado. La fija RN-069 y le da columna propia
  `V15__revocation_reason.sql`. **Con esto se suelta el freno que la propia historia
  señalaba** para `FEATURE_CATALOG`: la moderación ya tiene el camino de ida y el de
  vuelta. Encenderla sigue siendo una decisión aparte; las tres banderas
  —`seller-verification`, `publishing` y `catalog`— siguen apagadas por omisión.
- Catálogo, categorías, ficha de producto y favoritos. **HU-009 lo cerró el 27 de
  agosto de 2026**, salvo los favoritos: listado paginado por cursor, navegación
  por el árbol en sus dos niveles, ficha de producto y perfil público del
  vendedor, todo detrás de `FEATURE_CATALOG` y todo aplicando RN-068 —en el
  catálogo se ve solo lo `PUBLISHED`—. Con ella el ciclo de la fase deja de ser un
  callejón sin salida: lo que un moderador aprueba ya lo ve alguien sin cuenta.
  **Los favoritos salieron a su propia historia**, HU-011, porque el catálogo es
  anónimo y de lectura y ellos son de cuenta y de escritura, con reglas que no
  existían. **Quedó hecha el 2 de septiembre de 2026** y con ella este punto se
  cierra entero: el control en la ficha, la lista propia en `/mis-favoritos`
  paginada por cursor, y la intención que sobrevive al ingreso —quien no tiene
  sesión pulsa, entra, y vuelve con el favorito ya guardado (ADR-0029)—. Escribirla
  obligó a fijar las cuatro reglas que faltaban: RN-070 a RN-073, que responden por
  escrito lo que HU-009 dejó abierto —si un favorito sobrevive a que la publicación
  se archive o se venda, y si hay tope—. La tabla es `V16__favorites.sql`, con clave
  primaria sobre el par, que es lo que hace idempotente marcar dos veces sin leer
  antes de escribir.

  Destapó además dos defectos que estaban en producción y no eran suyos: el límite
  de los listados por cursor respondía 500 en vez del 400 que exige
  `contrato-api.md`, y cerrar sesión no borraba de la caché del navegador el perfil,
  las sesiones abiertas ni la lista de favoritos. Los dos arreglados, cada uno con
  la prueba que sí puede verlos.

  Queda fuera todavía la frase de la garantía del fabricante en la ficha (RN-067),
  aplazada a la tanda legal por decisión del 26 de agosto.
- Panel del vendedor con sus publicaciones y su estado. **Sigue a medias, pero por
  una sola cosa.** `/mis-publicaciones` llegó con HU-007 y da la lista con el
  estado de cada una, que es lo que hacía falta para retomar un borrador. Lo que
  faltaba era el panel: «ni cifras, ni ventas, ni el rastro de lo que pasó con cada
  publicación», y de esas tres ya solo falta una.

  **Las cifras las cerró HU-012 el 4 de septiembre de 2026**: una por cada estado de
  RN-061 encima de la lista, contadas por el servidor —`GET
  /api/v1/users/me/listings/summary`— y no sobre la página ya cargada, que habría
  atado la cifra al tamaño de esa página el día que la lista crezca. El cero se dice
  en vez de esconderse, porque omitir «0 en revisión» obliga a deducir por ausencia y
  quien deduce por ausencia no distingue «no tengo ninguna» de «esto no cargó». La
  fila es una lectura aparte de la lista, así que una cifra que no llega no puede
  tapar las publicaciones.

  La ruta no es la que la historia proponía. Decía `/api/v1/listings/mine/summary` y
  ese prefijo no existe: las publicaciones propias viven bajo `/api/v1/users/me`
  desde HU-007, y de ahí heredan la regla de seguridad que exige token.

  **Las ventas son Fase 3** y quedan fuera por alcance, no por olvido. **El rastro es
  HU-013**, escrita y todavía pendiente: necesita abrir para lectura el puerto
  `ModerationLog`, que hoy solo escribe, y obliga a escribir una regla nueva. Con
  ella este punto se cierra entero.

## Fase 3 — transacción

- Búsqueda y filtros con Typesense.
- Carrito y proceso de compra.
- Pago con Wompi: PSE, Nequi, tarjetas, Bancolombia a la mano y Addi.
- División del pago y retención de la comisión del 5%.
- Cotizador de envíos con Envía, Coordinadora e Interrapidísimo.
- Estados del pedido, confirmación de entrega por el comprador, ventana de
  reclamo de 3 días hábiles y liberación del pago (RN-034, RN-051, RN-052).
- Correos transaccionales de cada cambio de estado.
- Reseñas del vendedor.

## Fase 4 — operación y crecimiento

- Disputas y devoluciones: bandeja de reportes, resolución y reintegro. Las
  reglas que gobiernan el reporte existen desde Fase 1 porque el sitio las
  anuncia (RN-050 a RN-058); lo que llega aquí es el flujo que las ejecuta.
- Mensajería entre comprador y vendedor.
- Panel administrativo completo y reportes.
- Aplicación móvil.
- Notificaciones push.

## Decisiones aplazadas

Se documentan aquí para no reabrirlas por accidente:

| Tema | Estado |
|---|---|
| Días hábiles que tarda el desembolso en llegar a la cuenta del vendedor | Se define en Fase 3 con asesoría. La retención sí está definida: hasta que el comprador confirma o vence la ventana (RN-034) |
| Separar la política de devoluciones de los términos y condiciones | Sin decidir. Requiere variable de versión propia |
| Árbol de categorías del catálogo | **Decidido el 24 de agosto de 2026**, en `docs/producto/categorias.md`: seis familias y treinta y una categorías, por tipo de producto y sin eje de género, incluida la familia de tecnología. "Dama" y "Caballero" siguen sin ser categorías del proyecto |
| Facturación electrónica de la comisión ante la DIAN | Se define en Fase 3 |
| Vendedores con figura de empresa | Fuera de alcance por ahora |
| Pago contraentrega | Descartado por ahora |
| Regateo y ofertas | Sin decidir |
| Suscripción o destacados de pago | Sin decidir |
