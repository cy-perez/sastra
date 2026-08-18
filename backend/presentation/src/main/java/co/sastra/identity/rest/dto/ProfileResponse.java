package co.sastra.identity.rest.dto;

import org.jspecify.annotations.Nullable;

/**
 * El perfil tal como lo ve su dueno. Criterio 21.
 *
 * <p>Lleva el correo porque es la propia cuenta. Un perfil publico de vendedor
 * sera otro tipo y no incluira ni correo ni telefono
 * (docs/operacion/datos-personales.md).
 */
public record ProfileResponse(
        String email,
        boolean emailVerified,
        String displayName,
        @Nullable String city,
        @Nullable String phone) {}
