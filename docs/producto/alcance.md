# Alcance por fases

Regla: no se implementa funcionalidad de una fase posterior, aunque el diseño ya
la contemple y aunque sea fácil. Lo que no está en la fase actual, se anota.

## Fase 1 — cimientos, cuentas y sitio informativo

Es la fase en curso.

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
  y analiza, y `despliegue.yml` publica en `dev` con cada integración a `main` y
  en `prod` con etiqueta y aprobación. **Ejecutarlo por primera vez está aplazado
  por decisión**, no por falta de trabajo: el sitio se despliega —dominio y
  hospedaje— cuando el proyecto esté lo más completo posible. Hasta entonces se
  prueba en local integrado contra los servicios de GCP en capa gratuita, y los
  servicios de pago se contratan antes del lanzamiento inicial. El motivo está en
  `docs/operacion/entornos.md` y el procedimiento, listo para ese día, en
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
- Sobre Sastra (HU-005).
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
| Árbol de categorías del catálogo | Sin definir. "Dama" y "Caballero" no son categorías del proyecto: nadie las ha decidido |
| Facturación electrónica de la comisión ante la DIAN | Se define en Fase 3 |
| Vendedores con figura de empresa | Fuera de alcance por ahora |
| Pago contraentrega | Descartado por ahora |
| Regateo y ofertas | Sin decidir |
| Suscripción o destacados de pago | Sin decidir |
