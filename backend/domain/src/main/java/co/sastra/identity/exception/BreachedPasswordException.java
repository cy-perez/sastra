package co.sastra.identity.exception;

import co.sastra.shared.error.DomainException;
import co.sastra.shared.error.ErrorCode;

/**
 * RN-005: la contrasena aparece en una filtracion conocida (ADR-0013).
 */
public final class BreachedPasswordException extends DomainException {

    private static final long serialVersionUID = 1L;

    public BreachedPasswordException() {
        super(ErrorCode.AUTH_PASSWORD_BREACHED, "La contrasena aparece en una filtracion conocida");
    }
}
