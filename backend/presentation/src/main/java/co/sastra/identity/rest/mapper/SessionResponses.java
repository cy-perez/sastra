package co.sastra.identity.rest.mapper;

import co.sastra.identity.dto.SessionResult;
import co.sastra.identity.rest.dto.SessionResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Convierte el resultado de un caso de uso en el cuerpo de la respuesta.
 *
 * <p>Aqui ocurre la unica traduccion que no es un cambio de nombre: la caducidad
 * absoluta del token se convierte en segundos restantes. El cliente programa su
 * refresco con una duracion y asi no depende de que su reloj coincida con el del
 * servidor, que en un movil desincronizado no coincide.
 *
 * <p>El token de refresco se queda fuera del cuerpo a proposito: viaja solo en la
 * cookie {@code HttpOnly} (ADR-0003).
 */
@Component
public class SessionResponses {

    private final Clock reloj;

    public SessionResponses(Clock reloj) {
        this.reloj = reloj;
    }

    public SessionResponse de(SessionResult sesion) {
        long segundos =
                Duration.between(reloj.instant(), sesion.accessTokenExpiresAt()).toSeconds();

        List<String> roles =
                sesion.user().roles().stream().map(Enum::name).sorted().toList();

        return new SessionResponse(
                sesion.accessToken(),
                // Nunca negativo: un token recien emitido con reloj adelantado daria
                // un numero raro que el cliente interpretaria como caducado.
                Math.max(segundos, 0),
                new SessionResponse.SessionUser(
                        sesion.user().email(),
                        sesion.user().displayName(),
                        sesion.user().emailVerified(),
                        roles));
    }
}
