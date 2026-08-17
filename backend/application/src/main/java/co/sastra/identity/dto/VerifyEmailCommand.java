package co.sastra.identity.dto;

import org.jspecify.annotations.Nullable;

/**
 * @param token el valor en claro que viajaba en el enlace del correo. Se hashea
 *     antes de consultar: la base nunca ve el original
 * @param userAgent y {@code ipHash} describen el navegador que abre el enlace, que
 *     es el que se queda con la sesion del criterio 9
 */
public record VerifyEmailCommand(
        String token, @Nullable String userAgent, @Nullable String ipHash) {}
