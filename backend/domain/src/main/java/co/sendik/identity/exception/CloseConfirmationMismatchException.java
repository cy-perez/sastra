package co.sendik.identity.exception;

import co.sendik.shared.error.DomainException;
import co.sendik.shared.error.ErrorCode;

/**
 * Criterio 23: lo que se escribio para confirmar el cierre no coincide.
 *
 * <p>Cerrar una cuenta no se deshace, asi que la confirmacion no es un tramite:
 * es lo unico que separa un clic mal dado de perder el acceso.
 */
public final class CloseConfirmationMismatchException extends DomainException {

    private static final long serialVersionUID = 1L;

    public CloseConfirmationMismatchException() {
        super(ErrorCode.AUTH_CLOSE_CONFIRMATION_MISMATCH, "La confirmacion escrita no coincide");
    }
}
