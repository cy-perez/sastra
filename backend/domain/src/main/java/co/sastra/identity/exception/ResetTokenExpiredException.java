package co.sastra.identity.exception;

import co.sastra.shared.error.DomainException;
import co.sastra.shared.error.ErrorCode;

/** Criterio 18: el enlace de restablecimiento caduca a los 30 minutos. */
public final class ResetTokenExpiredException extends DomainException {

    private static final long serialVersionUID = 1L;

    public ResetTokenExpiredException() {
        super(ErrorCode.AUTH_RESET_TOKEN_EXPIRED, "El token de restablecimiento esta caducado");
    }
}
