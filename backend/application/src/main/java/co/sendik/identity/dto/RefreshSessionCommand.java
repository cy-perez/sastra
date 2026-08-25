package co.sendik.identity.dto;

import org.jspecify.annotations.Nullable;

/**
 * Renovacion de una sesion. Criterios 14 y 15.
 *
 * @param refreshToken el valor en claro que llego en la cookie. Se hashea antes de
 *     consultar: la base nunca ve el original. Admite nulo porque la cookie puede
 *     no llegar, y decidir que hacer con eso es del caso de uso: si el borde
 *     lanzara por su cuenta, habria dos sitios respondiendo a lo mismo
 * @param userAgent y {@code ipHash} se guardan en el token nuevo, no en el que se
 *     consume: describen esta peticion, no la de hace media hora
 */
public record RefreshSessionCommand(
        @Nullable String refreshToken,
        @Nullable String userAgent,
        @Nullable String ipHash) {}
