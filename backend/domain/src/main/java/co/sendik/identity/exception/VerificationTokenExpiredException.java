package co.sendik.identity.exception;

import co.sendik.shared.error.DomainException;
import co.sendik.shared.error.ErrorCode;

/**
 * RN-003: el enlace de verificacion caduco a las 24 horas.
 */
public final class VerificationTokenExpiredException extends DomainException {

    private static final long serialVersionUID = 1L;

    public VerificationTokenExpiredException() {
        super(ErrorCode.AUTH_VERIFICATION_TOKEN_EXPIRED, "El token esta caducado");
    }
}
