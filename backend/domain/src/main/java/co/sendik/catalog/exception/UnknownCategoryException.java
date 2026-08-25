package co.sendik.catalog.exception;

import co.sendik.catalog.model.CategoryId;
import co.sendik.shared.error.DomainException;
import co.sendik.shared.error.ErrorCode;

/** La categoria no esta en el arbol, o esta retirada y ya no admite publicaciones nuevas. */
public final class UnknownCategoryException extends DomainException {

    private static final long serialVersionUID = 1L;

    public UnknownCategoryException(CategoryId categoria) {
        super(ErrorCode.CATALOG_UNKNOWN_CATEGORY, "No se publica en la categoria " + categoria);
    }
}
