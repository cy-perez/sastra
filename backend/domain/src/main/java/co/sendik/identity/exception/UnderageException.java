package co.sendik.identity.exception;

import co.sendik.shared.error.DomainException;
import co.sendik.shared.error.ErrorCode;

/**
 * RN-008: la persona no ha cumplido 18 anos.
 */
public final class UnderageException extends DomainException {

    private static final long serialVersionUID = 1L;

    public UnderageException() {
        super(ErrorCode.AUTH_UNDERAGE, "La persona no alcanza la edad minima");
    }
}
