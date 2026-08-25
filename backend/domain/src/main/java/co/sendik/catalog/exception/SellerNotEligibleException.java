package co.sendik.catalog.exception;

import co.sendik.shared.error.DomainException;
import co.sendik.shared.error.ErrorCode;

/**
 * RN-011 y RN-013: para publicar hay que estar verificado.
 *
 * <p>Un mismo codigo para las dos situaciones —nunca se verifico, o le revocaron el
 * sello— porque lo que la persona tiene que hacer es lo mismo y termina en la misma
 * pantalla. Lo que RN-013 si preserva, y no toca esta excepcion, es que sus
 * publicaciones ya visibles siguen visibles: aqui solo se impide crear nuevas.
 */
public final class SellerNotEligibleException extends DomainException {

    private static final long serialVersionUID = 1L;

    public SellerNotEligibleException() {
        super(ErrorCode.CATALOG_SELLER_NOT_VERIFIED, "El vendedor no puede publicar hoy (RN-011, RN-013)");
    }
}
