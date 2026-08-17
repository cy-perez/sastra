package co.sastra.identity.exception;

import co.sastra.shared.error.DomainException;
import co.sastra.shared.error.ErrorCode;

/**
 * El enlace no corresponde a ningun token vigente, o ya se uso. RN-003 los hace de un solo uso.
 */
public final class VerificationTokenInvalidException extends DomainException {

    private static final long serialVersionUID = 1L;

    public VerificationTokenInvalidException() {
        super(ErrorCode.AUTH_VERIFICATION_TOKEN_INVALID, "El token no existe o ya fue usado");
    }
}
