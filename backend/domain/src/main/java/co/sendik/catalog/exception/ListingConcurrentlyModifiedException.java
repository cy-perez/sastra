package co.sendik.catalog.exception;

import co.sendik.catalog.model.ListingId;
import co.sendik.shared.error.DomainException;
import co.sendik.shared.error.ErrorCode;

/**
 * Alguien escribio sobre la misma publicacion entre la lectura y la escritura.
 * Criterio 34.
 *
 * <p>No es un caso raro: el vendedor edita mientras el moderador decide, y las dos
 * peticiones son legitimas. Gana la que llegue primero y la otra se entera.
 *
 * <p>Comparte codigo con las transiciones invalidas —{@code CATALOG_LISTING_INVALID_STATE},
 * un 409— porque lo que el cliente tiene que hacer es exactamente lo mismo: recargar y
 * mirar como esta la publicacion ahora. Distinguirlos le daria informacion sobre lo que
 * hizo otra persona sin cambiar en nada su siguiente paso.
 */
public final class ListingConcurrentlyModifiedException extends DomainException {

    private static final long serialVersionUID = 1L;

    public ListingConcurrentlyModifiedException(ListingId publicacion) {
        super(
                ErrorCode.CATALOG_LISTING_INVALID_STATE,
                "La publicacion " + publicacion + " cambio entre la lectura y la escritura");
    }
}
