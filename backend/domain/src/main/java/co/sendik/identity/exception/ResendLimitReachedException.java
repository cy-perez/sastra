package co.sendik.identity.exception;

import co.sendik.shared.error.DomainException;
import co.sendik.shared.error.ErrorCode;

/**
 * Se agotaron los reenvios permitidos dentro de la hora.
 */
public final class ResendLimitReachedException extends DomainException {

    private static final long serialVersionUID = 1L;

    public ResendLimitReachedException() {
        super(ErrorCode.AUTH_RESEND_LIMIT_REACHED, "Se alcanzo el limite de reenvios por hora");
    }
}
