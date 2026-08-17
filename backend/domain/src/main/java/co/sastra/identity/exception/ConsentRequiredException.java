package co.sastra.identity.exception;

import co.sastra.shared.error.DomainException;
import co.sastra.shared.error.ErrorCode;

/**
 * Falta aceptar los terminos o la politica de tratamiento de datos. Son dos casillas separadas y las dos son obligatorias (docs/operacion/datos-personales.md).
 */
public final class ConsentRequiredException extends DomainException {

    private static final long serialVersionUID = 1L;

    public ConsentRequiredException() {
        super(ErrorCode.AUTH_CONSENT_REQUIRED, "Falta el consentimiento de un documento obligatorio");
    }
}
