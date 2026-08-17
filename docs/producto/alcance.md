# Alcance por fases

Regla: no se implementa funcionalidad de una fase posterior, aunque el diseño ya
la contemple y aunque sea fácil. Lo que no está en la fase actual, se anota.

## Fase 1 — cimientos, cuentas y sitio informativo

Es la fase en curso.

**Plataforma**
- Monorepo con backend y frontend, Gradle multi-módulo y Angular con SSR.
- Sistema de diseño implementado: tokens, componentes base, modo oscuro,
  selector de idioma, controles de accesibilidad.
- Internacionalización ES/EN funcionando con SSR.
- Canalización de integración continua: compilar, probar, analizar y desplegar.
- Base de datos con migraciones y esquema inicial de usuarios.

**Cuentas**
- Registro de comprador y de vendedor con correo y contraseña.
- Verificación de correo.
- Inicio y cierre de sesión con JWT propio y refresco rotatorio.
- Recuperación de contraseña.
- Perfil básico editable.
- Cierre de cuenta y descarga de datos personales.

**Sitio informativo**
- Portada con la propuesta de valor y las tres tarjetas de confianza.
- Cómo funciona, para comprador y para vendedor.
- Sobre Sastra.
- Preguntas frecuentes.
- Contacto.
- Términos y condiciones, política de tratamiento de datos, política de cookies.

**Fuera de esta fase:** publicar productos, catálogo, búsqueda, carrito, pagos,
envíos, verificación de identidad.

## Fase 2 — publicación y catálogo

- Verificación de vendedor: identidad, selfie y cuenta bancaria.
- Asistente de captura de fotos con overlay, nivelador y recorte en cliente.
- Publicación de prenda con las cuatro tomas obligatorias y las intermedias.
- Visor 360º en la ficha de producto.
- Panel de moderación y flujo de aprobación o rechazo con motivo.
- Catálogo, categorías, ficha de producto y favoritos.
- Panel del vendedor con sus publicaciones y su estado.

## Fase 3 — transacción

- Búsqueda y filtros con Typesense.
- Carrito y proceso de compra.
- Pago con Wompi: PSE, Nequi, tarjetas, Bancolombia a la mano y Addi.
- División del pago y retención de la comisión del 5%.
- Cotizador de envíos con Envía, Coordinadora e Interrapidísimo.
- Estados del pedido, confirmación de entrega y liberación del pago.
- Correos transaccionales de cada cambio de estado.
- Reseñas del vendedor.

## Fase 4 — operación y crecimiento

- Disputas y devoluciones.
- Mensajería entre comprador y vendedor.
- Panel administrativo completo y reportes.
- Aplicación móvil.
- Notificaciones push.

## Decisiones aplazadas

Se documentan aquí para no reabrirlas por accidente:

| Tema | Estado |
|---|---|
| Tiempos legales de retención y desembolso | Se define en Fase 3 con asesoría |
| Facturación electrónica de la comisión ante la DIAN | Se define en Fase 3 |
| Vendedores con figura de empresa | Fuera de alcance por ahora |
| Pago contraentrega | Descartado por ahora |
| Regateo y ofertas | Sin decidir |
| Suscripción o destacados de pago | Sin decidir |
