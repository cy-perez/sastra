package co.sendik.catalog.rest.mapper;

import co.sendik.catalog.dto.CatalogPage;
import co.sendik.catalog.rest.dto.CatalogPageResponse;
import co.sendik.catalog.rest.dto.PublicListingResponse;
import co.sendik.shared.port.out.PublicFileStore;
import java.util.List;

/**
 * Del tramo de aplicacion al cuerpo de la respuesta. HU-009, criterio 3.
 *
 * <p>Un mapeador propio y no dos lineas en cada controlador: el catalogo general y el
 * escaparate de un vendedor devuelven la misma forma, y escrita dos veces tarde o temprano
 * una de las dos deja de usar {@code ListingResponses.publica}. Ese metodo es el que
 * garantiza que no salga nada de moderacion.
 */
public final class CatalogPages {

    private CatalogPages() {}

    public static CatalogPageResponse de(CatalogPage tramo, PublicFileStore almacen) {
        List<PublicListingResponse> items = tramo.items().stream()
                .map(publicacion -> ListingResponses.publica(publicacion, almacen))
                .toList();

        return new CatalogPageResponse(items, CatalogCursors.texto(tramo.siguiente()), tramo.hayMas());
    }
}
