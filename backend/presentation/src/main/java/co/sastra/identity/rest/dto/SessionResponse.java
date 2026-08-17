package co.sastra.identity.rest.dto;

import java.util.List;

/**
 * Respuesta de todo lo que abre o renueva una sesion: ingreso, refresco y
 * verificacion de correo.
 *
 * <p><strong>El token de refresco no esta aqui y no es un olvido.</strong> Viaja
 * solo en la cookie {@code HttpOnly}, que el JavaScript no puede leer. Devolverlo
 * tambien en el cuerpo anularia esa proteccion: cualquier script inyectado en la
 * pagina podria quedarse con una credencial de 30 dias (ADR-0003).
 *
 * @param expiresIn segundos que le quedan al token de acceso, no una fecha. El
 *     cliente programa el refresco con una duracion y asi no depende de que su
 *     reloj coincida con el del servidor
 * @param user lo minimo para pintar la cabecera. {@code emailVerified} es lo que
 *     usa el frontend para el criterio 13
 */
public record SessionResponse(String accessToken, long expiresIn, SessionUser user) {

    /** El resumen del usuario. Tipo propio de la API: no se filtra el del dominio. */
    public record SessionUser(String email, String displayName, boolean emailVerified, List<String> roles) {}
}
