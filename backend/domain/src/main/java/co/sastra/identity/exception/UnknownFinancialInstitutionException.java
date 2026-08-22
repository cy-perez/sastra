package co.sastra.identity.exception;

import co.sastra.shared.error.DomainException;
import co.sastra.shared.error.ErrorCode;

/**
 * La entidad financiera no esta en el catalogo, o esta desactivada.
 *
 * <p>Existe para que el fallo llegue como un error de negocio y no como una
 * violacion de clave ajena. La clave ajena tambien lo impide —es la ultima
 * cerradura— pero por ahi sale un 500 y la persona no sabe que su banco no esta en
 * la lista.
 */
public final class UnknownFinancialInstitutionException extends DomainException {

    private static final long serialVersionUID = 1L;

    public UnknownFinancialInstitutionException(String entidad) {
        super(ErrorCode.SELLER_UNKNOWN_INSTITUTION, "La entidad " + entidad + " no esta en el catalogo activo");
    }
}
