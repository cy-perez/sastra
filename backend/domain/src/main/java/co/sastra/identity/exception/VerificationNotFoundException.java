package co.sastra.identity.exception;

import co.sastra.identity.model.SellerVerificationId;
import co.sastra.shared.error.DomainException;
import co.sastra.shared.error.ErrorCode;

/**
 * No existe la verificacion que el moderador pidio.
 *
 * <p>Reutiliza {@code COMMON_NOT_FOUND} y no un codigo propio: hacia afuera solo hay
 * que decir que no esta, y un codigo especifico de verificacion permitiria a cualquiera
 * distinguir «esta solicitud no existe» de «existe y no es tuya», que es contar algo.
 */
public final class VerificationNotFoundException extends DomainException {

    private static final long serialVersionUID = 1L;

    public VerificationNotFoundException(SellerVerificationId verificacion) {
        super(ErrorCode.COMMON_NOT_FOUND, "No existe la verificacion " + verificacion);
    }
}
