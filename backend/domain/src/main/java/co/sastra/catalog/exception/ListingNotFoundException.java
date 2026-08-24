package co.sastra.catalog.exception;

import co.sastra.catalog.model.ListingId;
import co.sastra.shared.error.DomainException;
import co.sastra.shared.error.ErrorCode;

/**
 * No existe la publicacion, o quien pregunta no tiene por que verla.
 *
 * <p><strong>Las dos cosas con el mismo codigo, y a proposito.</strong> El criterio 33
 * lo pide para quien no esta autenticado —el estado de una publicacion que no es
 * visible no se revela— y vale igual para un vendedor que toca la de otro: un 403
 * confirmaria que existe, que es justo lo que no se cuenta.
 *
 * <p>Reutiliza {@code COMMON_NOT_FOUND}, como la verificacion, y por lo mismo: hacia
 * afuera solo hay que decir que no esta.
 */
public final class ListingNotFoundException extends DomainException {

    private static final long serialVersionUID = 1L;

    public ListingNotFoundException(ListingId publicacion) {
        super(ErrorCode.COMMON_NOT_FOUND, "No existe la publicacion " + publicacion + ", o no es de quien pregunta");
    }
}
