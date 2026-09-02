package co.sendik.catalog.rest.mapper;

import co.sendik.catalog.dto.FavoritePage;
import co.sendik.catalog.rest.dto.CatalogPageResponse;
import co.sendik.catalog.rest.dto.PublicListingResponse;
import co.sendik.shared.port.out.PublicFileStore;
import java.util.List;

/**
 * Del tramo de favoritos al cuerpo de la respuesta. HU-011, criterio 12.
 *
 * <p><strong>Devuelve {@link CatalogPageResponse}, la misma forma que el catalogo.</strong>
 * La lista de favoritos pinta las mismas tarjetas con la misma rejilla y pagina igual, asi
 * que una forma propia obligaria al cliente a tener dos mapeadores para lo mismo. Lo unico
 * que cambia es de donde sale el cursor, y eso no se ve desde fuera: es opaco.
 *
 * <p>Cada elemento pasa por {@code ListingResponses.publica}, que es el metodo que
 * garantiza que no salga nada de moderacion. Aqui importa igual que en el catalogo: quien
 * mira su lista de favoritos no es el dueno de lo que hay en ella.
 */
public final class FavoritePages {

    private FavoritePages() {}

    public static CatalogPageResponse de(FavoritePage tramo, PublicFileStore almacen) {
        List<PublicListingResponse> items = tramo.items().stream()
                .map(publicacion -> ListingResponses.publica(publicacion, almacen))
                .toList();

        return new CatalogPageResponse(items, FavoriteCursors.texto(tramo.siguiente()), tramo.hayMas());
    }
}
