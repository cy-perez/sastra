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
5. La persona acepta los términos y la política de tratamiento de datos en **dos
   casillas separadas**, ninguna marcada por omisión. Una sola casilla para las
   dos cosas no es consentimiento válido
   (`docs/operacion/datos-personales.md`). De cada una se guarda su propia
   evidencia: documento, versión, fecha, hora y dirección IP.
6. La persona declara su fecha de nacimiento y el registro se rechaza si no ha
   cumplido 18 años (RN-008). La fecha se guarda, no solo el resultado de la
   comprobación, porque se confirma contra el documento en la verificación de
   identidad de Fase 2.

**Verificación**

7. El enlace caduca a las 24 horas y funciona una sola vez.
8. Con un enlace caducado se ofrece reenviar, con un máximo de tres reenvíos por
   hora.
9. Verificado el correo, la cuenta queda activa y la persona entra directamente.

**Inicio de sesión**

10. Credenciales correctas devuelven token de acceso de 15 minutos y token de
    refresco de 30 días en cookie `HttpOnly`, `Secure`, `SameSite=Strict`.
11. Credenciales incorrectas devuelven siempre el mismo mensaje genérico y el
    mismo tiempo de respuesta, sin distinguir si falló el correo o la clave.
12. Al quinto intento fallido la cuenta se bloquea 15 minutos y se avisa al
    titular por correo.
13. Una cuenta sin verificar puede entrar pero solo ve el aviso de verificación
    pendiente y el botón de reenvío.

**Sesión**

14. El token de refresco rota en cada uso; el anterior queda inválido.
15. Si llega un token de refresco ya usado, se revoca toda la familia de tokens
    de ese usuario y se le notifica.
16. Cerrar sesión revoca el token de refresco en el servidor, no solo en el
    navegador.
17. La persona puede ver sus sesiones activas y cerrarlas.

**Recuperación**

18. El enlace caduca a los 30 minutos y es de un solo uso.
19. La respuesta es idéntica exista o no el correo.
20. Al cambiar la contraseña se cierran todas las sesiones y se notifica.

**Perfil y cierre**

21. La persona edita nombre, ciudad, teléfono y foto. Cambiar el correo exige
    verificar el nuevo antes de reemplazar el anterior. La foto va en su propia
    rebanada: necesita almacenamiento de archivos y una ADR.
22. Puede descargar sus datos en un archivo legible.
23. Puede cerrar su cuenta previa confirmación escrita.

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
`POST /api/v1/auth/resend-verification`, `POST /api/v1/auth/login`,
`POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout`,
`POST /api/v1/auth/forgot-password`, `POST /api/v1/auth/reset-password`,
`POST /api/v1/auth/confirm-email-change`, `GET|PUT /api/v1/users/me`,
`POST /api/v1/users/me/email`, `POST /api/v1/users/me/email-verification`,
`GET|DELETE /api/v1/users/me/sessions`, `GET /api/v1/users/me/export`,
`DELETE /api/v1/users/me`.

El perfil se guarda con **PUT y no con PATCH**. Con PATCH habría que distinguir
"no mandé este campo" de "lo dejé vacío", y esa distinción es justo donde se
pierde el borrado de un dato opcional: la ciudad vaciada llegaría como ausencia
y el servidor la dejaría como estaba. Con PUT se manda el perfil entero y se
guarda entero.

La confirmación del correo nuevo cuelga de `auth` y no de `users/me` porque se
llega abriendo un enlace del correo, y ese correo se abre la mitad de las veces
en otro dispositivo, sin sesión. La credencial es el token del enlace.

Tablas: `users`, `user_credentials`, `refresh_tokens`, `verification_tokens`,
`login_attempts`, `consents`.

Correos transaccionales: verificación, restablecimiento, aviso de bloqueo, aviso
de cambio de contraseña, aviso de intento de registro con correo existente,
confirmación del correo nuevo, aviso al correo anterior de que el correo cambió,
aviso a quien ya tenía cuenta de que alguien intentó mover la suya a esa
dirección, aviso de cuenta cerrada.

Dependencias externas, ambas detrás de un puerto en `application`: el envío de
correo con Resend (ADR-0012) y la comprobación de contraseñas filtradas con Have
I Been Pwned por k-anonimato (ADR-0013). La segunda falla abierta: si no
responde a tiempo, la contraseña se acepta y se registra el evento. El mínimo de
diez caracteres se comprueba siempre en el dominio, sin salir a la red, así que
el criterio 3 necesita dos códigos de error distintos para poder explicar cuál
de las dos reglas falló.

## Pruebas requeridas

- Unitarias de dominio: política de contraseña, caducidad de tokens, conteo de
  intentos, transición de estados de cuenta.
- Integración con Testcontainers: rotación y revocación de refresco, unicidad de
  correo bajo concurrencia.
- Extremo a extremo: registro, verificación, ingreso, cierre y recuperación.
- Seguridad: respuestas indistinguibles en registro y recuperación, ausencia del
  hash de contraseña en cualquier respuesta, cookies con todos sus atributos.
