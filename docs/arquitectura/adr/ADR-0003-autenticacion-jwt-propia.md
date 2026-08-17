# ADR-0003 — Autenticación con JWT propio y refresco rotatorio

**Fecha:** 2026-08-15 · **Estado:** aceptada

## Contexto

Se necesita autenticar compradores y vendedores en web y, más adelante, en una
aplicación móvil.

## Opciones

1. Identity Platform de Google. Rápido de montar, menos código de seguridad
   propio, y dependencia de un proveedor en el punto más sensible del producto.
2. Keycloak autoalojado. Completo y estándar, pero es un servicio más que operar
   y mantener para un desarrollador solo.
3. JWT propio con Spring Security.

## Decisión

JWT propio con Spring Security 7.1.

## Motivo

La autenticación está atada al modelo de usuario, a los roles y al proceso de
verificación del vendedor, que es específico del negocio. Delegarla obliga a
sincronizar dos fuentes de verdad sobre quién es cada persona.

Spring Security cubre la parte difícil (filtros, contexto, autorización) y el
resto es acotado: emitir, validar, rotar y revocar. No se está escribiendo
criptografía, se está usando la de la plataforma.

Con un proveedor externo, además, cada cambio de política de sesión pasa por su
consola y su modelo de datos.

## Detalle

- Acceso: 15 minutos, en memoria del cliente. Nunca en `localStorage`.
- Refresco: 30 días, en cookie `HttpOnly`, `Secure`, `SameSite=Strict`, con ruta
  limitada a `/api/v1/auth`.
- Rotación en cada uso. Al detectar reutilización de un token ya consumido se
  revoca toda la familia y se notifica al usuario.
- Contraseñas con Argon2id.
- Se guarda el hash del token, nunca el token.

## Consecuencias

- Hay que implementar y probar rotación, revocación y bloqueo por intentos.
- El inicio de sesión con Google o Apple se agrega después como método
  adicional, no como reemplazo del modelo.
- Cero dependencia de un proveedor externo en el camino crítico de acceso.

## Cuándo revisar

Si aparecen requisitos de inicio de sesión único empresarial o de cumplimiento
que superen lo razonable de mantener a mano.
