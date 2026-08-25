package co.sendik.identity.dto;

import org.jspecify.annotations.Nullable;

/**
 * Un intento de ingreso, ya extraido del HTTP.
 *
 * @param userAgent con que navegador se abrio la sesion. Sirve para que la persona
 *     reconozca sus propias sesiones; es opcional porque una peticion sin
 *     {@code User-Agent} es rara pero valida
 * @param ipHash la IP ya hasheada por el borde. Aqui nunca llega en claro
 */
public record LoginCommand(
        String email,
        String password,
        @Nullable String userAgent,
        @Nullable String ipHash) {}
