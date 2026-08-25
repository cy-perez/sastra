package co.sendik.identity.exception;

import co.sendik.shared.error.DomainException;
import co.sendik.shared.error.ErrorCode;

/**
 * RN-012: el titular de la cuenta bancaria no es el del documento.
 *
 * <p>Se detecta al registrar la cuenta y no al revisar, porque a la persona le sirve
 * saberlo en el momento: casi siempre es que escribio su nombre de dos formas
 * distintas y lo corrige en el acto. Un rechazo dos dias despues por lo mismo seria
 * el mismo error con dos dias de espera encima.
 *
 * <p><strong>No lleva ningun nombre en el mensaje.</strong> Los dos son datos
 * personales, y {@code docs/operacion/datos-personales.md} prohibe que un registro
 * los contenga, tambien en el mensaje de una excepcion.
 */
public final class AccountHolderMismatchException extends DomainException {

    private static final long serialVersionUID = 1L;

    public AccountHolderMismatchException() {
        super(
                ErrorCode.SELLER_ACCOUNT_HOLDER_MISMATCH,
                "El titular de la cuenta no coincide con el del documento (RN-012)");
    }
}
