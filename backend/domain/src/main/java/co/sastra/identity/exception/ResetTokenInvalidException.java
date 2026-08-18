package co.sastra.identity.exception;

import co.sastra.shared.error.DomainException;
import co.sastra.shared.error.ErrorCode;

/**
 * El enlace de restablecimiento no existe o ya se uso. Criterio 18.
 *
 * <p>Es distinta de {@link VerificationTokenInvalidException} aunque el mecanismo
 * sea el mismo: lo que cambia es lo que hay que decirle a la persona, porque los
 * dos enlaces duran cosas distintas.
 */
public final class ResetTokenInvalidException extends DomainException {

    private static final long serialVersionUID = 1L;

    public ResetTokenInvalidException() {
        super(ErrorCode.AUTH_RESET_TOKEN_INVALID, "El token de restablecimiento no es utilizable");
    }
}
