package co.sendik.catalog.exception;

import co.sendik.shared.error.DomainException;
import co.sendik.shared.error.ErrorCode;

/**
 * RN-016 y RN-017: no estan todas las tomas que esta publicacion exige.
 *
 * <p>El mensaje lleva cuantas se esperaban y cuantas hay, porque las dos cifras
 * cambian segun el producto: ocho en general, cuatro si es tecnologia sellada.
 */
public final class ShotsIncompleteException extends DomainException {

    private static final long serialVersionUID = 1L;

    public ShotsIncompleteException(int exigidas, int presentes, String detalle) {
        super(
                ErrorCode.CATALOG_SHOTS_INCOMPLETE,
                "Se exigen " + exigidas + " tomas y hay " + presentes + ": " + detalle);
    }
}
