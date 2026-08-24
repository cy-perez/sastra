package co.sastra.catalog.usecase;

import co.sastra.catalog.exception.ListingNotFoundException;
import co.sastra.catalog.model.Listing;
import co.sastra.catalog.model.ListingId;
import co.sastra.catalog.model.SellerId;
import co.sastra.catalog.port.out.ListingRepository;

/**
 * Cargar una publicacion comprobando quien la pide.
 *
 * <p>Existe para que la comprobacion sea la misma en los once casos de uso que la
 * necesitan. Escrita once veces, tarde o temprano una se escribe distinta, y esa es la
 * que deja a alguien editar la publicacion de otro.
 *
 * <p><strong>Que no sea suya se responde igual que que no exista.</strong> Un 403
 * confirmaria que la publicacion existe, y eso ya es contar algo (criterio 33).
 */
final class ListingAccess {

    private ListingAccess() {}

    static Listing deVendedor(ListingRepository publicaciones, ListingId id, SellerId vendedor) {
        return publicaciones
                .buscar(id)
                .filter(publicacion -> publicacion.sellerId().equals(vendedor))
                .orElseThrow(() -> new ListingNotFoundException(id));
    }

    static Listing cualquiera(ListingRepository publicaciones, ListingId id) {
        return publicaciones.buscar(id).orElseThrow(() -> new ListingNotFoundException(id));
    }
}
