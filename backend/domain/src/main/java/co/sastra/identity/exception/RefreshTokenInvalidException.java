package co.sastra.identity.exception;

import co.sastra.shared.error.DomainException;
import co.sastra.shared.error.ErrorCode;

/**
 * RN-007: el token de refresco no sirve para renovar la sesion.
 *
 * <p>Una sola excepcion para las cuatro causas, que son no existir, haber
 * caducado, estar revocado y haberse usado ya. Hacia afuera son indistinguibles
 * porque la reaccion del cliente es siempre la misma: pedir las credenciales otra
 * vez. La cuarta si tiene consecuencias adentro, y de eso se encarga el caso de
 * uso antes de lanzar esto (criterio 15).
 */
public final class RefreshTokenInvalidException extends DomainException {

    private static final long serialVersionUID = 1L;

    public RefreshTokenInvalidException() {
        super(ErrorCode.AUTH_SESSION_INVALID, "El token de refresco no es utilizable");
    }
}
