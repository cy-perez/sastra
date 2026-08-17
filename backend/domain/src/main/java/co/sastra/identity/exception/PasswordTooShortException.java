package co.sastra.identity.exception;

import co.sastra.shared.error.DomainException;
import co.sastra.shared.error.ErrorCode;

/**
 * RN-005: la contrasena no llega al minimo de caracteres.
 */
public final class PasswordTooShortException extends DomainException {

    private static final long serialVersionUID = 1L;

    public PasswordTooShortException() {
        super(ErrorCode.AUTH_PASSWORD_TOO_SHORT, "La contrasena no alcanza el largo minimo");
    }
}
