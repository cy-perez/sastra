package co.sendik.identity.usecase;

import co.sendik.identity.dto.ActiveSession;
import co.sendik.identity.model.TokenFamilyId;
import co.sendik.identity.model.UserId;
import co.sendik.identity.port.out.RefreshTokenRepository;
import java.time.Clock;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Las sesiones abiertas de quien pregunta. Criterio 17.
 *
 * <p>Solo las suyas: el identificador sale del token de acceso y no de la
 * peticion, asi que no hay forma de pedir las de otra persona.
 *
 * <p>No comprueba que la cuenta exista, al reves que la exportacion y el cierre.
 * Con una cuenta cerrada devuelve una lista vacia, que es la verdad: sus sesiones
 * se revocaron todas. Anadir la consulta solo para responder 401 costaria un viaje
 * a la base en cada listado a cambio de nada.
 *
 * <p>Marca cual es la actual comparando con el {@code sid} del token con el que se
 * llamo. Ese dato tiene que venir del borde porque la cookie de refresco no llega
 * a estas rutas: su ruta esta limitada a {@code /api/v1/auth}, que es
 * precisamente lo que la protege de CSRF.
 */
public class ListSessionsUseCase {

    private final RefreshTokenRepository refrescos;
    private final Clock reloj;

    public ListSessionsUseCase(RefreshTokenRepository refrescos, Clock reloj) {
        this.refrescos = refrescos;
        this.reloj = reloj;
    }

    /**
     * @param sesionActual el {@code sid} del token de acceso. Puede ser nulo si el
     *     token se emitio antes de que ese claim existiera: entonces no se marca
     *     ninguna como actual, que es mejor que marcar la equivocada
     */
    public List<ActiveSession> execute(UserId usuario, @Nullable TokenFamilyId sesionActual) {
        return refrescos.listarSesionesActivasDe(usuario, reloj.instant()).stream()
                .map(token -> new ActiveSession(
                        token.familyId().toString(),
                        token.userAgent(),
                        token.createdAt(),
                        token.expiresAt(),
                        token.familyId().equals(sesionActual)))
                .toList();
    }
}
