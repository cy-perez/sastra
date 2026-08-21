package co.sastra.identity.rest.dto;

import org.jspecify.annotations.Nullable;

/**
 * El perfil tal como lo ve su dueno. Criterio 21.
 *
 * <p>Lleva el correo porque es la propia cuenta. Un perfil publico de vendedor
 * sera otro tipo y no incluira ni correo ni telefono
 * (docs/operacion/datos-personales.md).
 *
 * <p>De la foto sale la <strong>direccion</strong>, no la clave con la que esta
 * guardada. La clave es un detalle del almacen y el cliente no la necesita para
 * nada: lo que hace con la foto es pintarla. La compone el almacen, que es quien
 * conoce su configuracion (ADR-0018).
 */
public record ProfileResponse(
        String email,
        boolean emailVerified,
        String displayName,
        @Nullable String city,
        @Nullable String phone,
        @Nullable String avatarUrl) {}
