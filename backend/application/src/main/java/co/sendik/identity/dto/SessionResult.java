package co.sendik.identity.dto;

import java.time.Instant;

/**
 * Una sesion recien emitida. Es lo que devuelven el ingreso, el refresco y la
 * verificacion de correo.
 *
 * <p>Los dos tokens viajan juntos hasta el borde HTTP y ahi se separan: el de
 * acceso va en el cuerpo de la respuesta, para que viva en memoria del cliente, y
 * el de refresco va en una cookie {@code HttpOnly} que el JavaScript no puede
 * leer. Nunca al reves y nunca los dos por el mismo canal (ADR-0003).
 *
 * @param refreshToken el valor en claro. Existe solo entre este resultado y la
 *     cookie: la base de datos guarda unicamente su hash
 */
public record SessionResult(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        AuthenticatedUser user) {

    /**
     * Sin ninguno de los dos tokens.
     *
     * <p>El {@code toString} que genera un record los imprimiria en claro, y basta
     * un {@code LOG.debug("sesion={}", resultado)} de cualquier cambio futuro para
     * escribir una credencial de 30 dias en el registro del servidor. El perfil
     * local ya trae {@code co.sendik: DEBUG}, asi que no haria falta ni una
     * equivocacion en produccion (docs/operacion/datos-personales.md).
     */
    @Override
    public String toString() {
        return "SessionResult[acceso hasta " + accessTokenExpiresAt + ", refresco hasta " + refreshTokenExpiresAt + "]";
    }
}
