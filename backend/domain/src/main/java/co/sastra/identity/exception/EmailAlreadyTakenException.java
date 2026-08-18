package co.sastra.identity.exception;

import co.sastra.shared.error.DomainException;
import co.sastra.shared.error.ErrorCode;

/**
 * Criterio 21 y RN-001: ese correo ya tiene cuenta.
 *
 * <p>Solo aparece al confirmar un cambio de correo, nunca al pedirlo. Entre pedir
 * y confirmar puede pasar un dia, y en ese hueco alguien pudo registrarse con esa
 * direccion.
 */
public final class EmailAlreadyTakenException extends DomainException {

    private static final long serialVersionUID = 1L;

    public EmailAlreadyTakenException() {
        super(ErrorCode.AUTH_EMAIL_TAKEN, "Ese correo ya tiene cuenta");
    }
}
