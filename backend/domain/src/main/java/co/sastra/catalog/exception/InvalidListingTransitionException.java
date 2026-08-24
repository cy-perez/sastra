package co.sastra.catalog.exception;

import co.sastra.catalog.model.ListingStatus;
import co.sastra.shared.error.DomainException;
import co.sastra.shared.error.ErrorCode;

/** RN-061: esa transicion no existe. */
public final class InvalidListingTransitionException extends DomainException {

    private static final long serialVersionUID = 1L;

    public InvalidListingTransitionException(ListingStatus desde, ListingStatus hacia) {
        super(ErrorCode.CATALOG_LISTING_INVALID_STATE, "Transicion invalida de " + desde + " a " + hacia + " (RN-061)");
    }
}
