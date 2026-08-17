# HU-001 — Registro y autenticación

**Fase:** 1 | **Estado:** pendiente
**Reglas:** RN-001 a RN-009

## Objetivo

Una persona puede crear su cuenta en Sastra, verificar su correo, entrar, salir y
recuperar el acceso si olvida la contraseña.

## Alcance

Entra: registro con correo y contraseña, verificación por correo, inicio y cierre
de sesión, refresco de sesión, recuperación de contraseña, perfil básico
editable, cierre de cuenta.

No entra: inicio de sesión con Google o Apple, verificación por SMS,
autenticación en dos pasos, verificación de identidad del vendedor. Todo eso se
evalúa después y no debe condicionar el diseño de esta historia más allá de
dejar el modelo abierto a varios métodos de autenticación.

## Criterios de aceptación

**Registro**

1. Dado un correo no registrado, cuando la persona envía correo, contraseña y
   nombre, entonces se crea la cuenta en estado no verificado y se envía el
   correo de verificación.
2. Dado un correo ya registrado, cuando se intenta registrar, entonces la
   respuesta es la misma que en el caso exitoso y no se revela que el correo
   existe. Se envía en cambio un aviso al titular. Esto evita que el formulario
   sirva para averiguar quién tiene cuenta.
3. La contraseña se rechaza si tiene menos de 10 caracteres o si aparece en la
   lista de contraseñas filtradas. El mensaje explica cuál de las dos falló.
4. El indicador de fortaleza es orientativo y no bloquea el envío.
5. La persona debe aceptar términos y política de tratamiento de datos con una
   casilla explícita, sin marcar por omisión. Se guarda fecha, hora, versión del
   documento y dirección IP.

**Verificación**

6. El enlace caduca a las 24 horas y funciona una sola vez.
7. Con un enlace caducado se ofrece reenviar, con un máximo de tres reenvíos por
   hora.
8. Verificado el correo, la cuenta queda activa y la persona entra directamente.

**Inicio de sesión**

9. Credenciales correctas devuelven token de acceso de 15 minutos y token de
   refresco de 30 días en cookie `HttpOnly`, `Secure`, `SameSite=Strict`.
10. Credenciales incorrectas devuelven siempre el mismo mensaje genérico y el
    mismo tiempo de respuesta, sin distinguir si falló el correo o la clave.
11. Al quinto intento fallido la cuenta se bloquea 15 minutos y se avisa al
    titular por correo.
12. Una cuenta sin verificar puede entrar pero solo ve el aviso de verificación
    pendiente y el botón de reenvío.

**Sesión**

13. El token de refresco rota en cada uso; el anterior queda inválido.
14. Si llega un token de refresco ya usado, se revoca toda la familia de tokens
    de ese usuario y se le notifica.
15. Cerrar sesión revoca el token de refresco en el servidor, no solo en el
    navegador.
16. La persona puede ver sus sesiones activas y cerrarlas.

**Recuperación**

17. El enlace caduca a los 30 minutos y es de un solo uso.
18. La respuesta es idéntica exista o no el correo.
19. Al cambiar la contraseña se cierran todas las sesiones y se notifica.

**Perfil y cierre**

20. La persona edita nombre, ciudad, teléfono y foto. Cambiar el correo exige
    verificar el nuevo antes de reemplazar el anterior.
21. Puede descargar sus datos en un archivo legible.
22. Puede cerrar su cuenta previa confirmación escrita.

## Casos borde

- Doble envío del formulario: la operación es idempotente, no crea dos cuentas.
- Correo con mayúsculas o alias con punto: se normaliza antes de comparar.
- Reloj del servidor y caducidad: siempre en UTC.
- Cookie bloqueada por el navegador: mensaje claro, no falla en silencio.
- Registro durante SSR: el formulario funciona con JavaScript deshabilitado hasta
  donde sea razonable, y nunca deja el botón en estado de carga permanente.

## Diseño

- Formularios según la guía visual: campo con etiqueta visible, error con
  `aria-describedby`, foco de 3px.
- Un solo botón con acento ocre por pantalla.
- Las pantallas de correo enviado y de error usan los estados vacío y error ya
  definidos en `marca.css`.
- En móvil, teclado adecuado por tipo de campo y sin desplazamiento horizontal.
- Todos los textos en ES y EN.

## Notas técnicas

Endpoints: `POST /api/v1/auth/register`, `POST /api/v1/auth/verify-email`,
`POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`,
`POST /api/v1/auth/logout`, `POST /api/v1/auth/forgot-password`,
`POST /api/v1/auth/reset-password`, `GET|PATCH /api/v1/users/me`,
`DELETE /api/v1/users/me`.

Tablas: `users`, `user_credentials`, `refresh_tokens`, `verification_tokens`,
`login_attempts`, `consents`.

Correos transaccionales: verificación, restablecimiento, aviso de bloqueo, aviso
de cambio de contraseña, aviso de intento de registro con correo existente.

## Pruebas requeridas

- Unitarias de dominio: política de contraseña, caducidad de tokens, conteo de
  intentos, transición de estados de cuenta.
- Integración con Testcontainers: rotación y revocación de refresco, unicidad de
  correo bajo concurrencia.
- Extremo a extremo: registro, verificación, ingreso, cierre y recuperación.
- Seguridad: respuestas indistinguibles en registro y recuperación, ausencia del
  hash de contraseña en cualquier respuesta, cookies con todos sus atributos.
