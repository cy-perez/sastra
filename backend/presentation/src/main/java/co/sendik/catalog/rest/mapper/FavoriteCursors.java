package co.sendik.catalog.rest.mapper;

import co.sendik.catalog.dto.FavoriteCursor;
import co.sendik.catalog.model.ListingId;
import org.jspecify.annotations.Nullable;

/**
 * El cursor de la lista de favoritos, de par de valores a cadena opaca y vuelta. HU-011,
 * criterio 12.
 *
 * <p>Comparte con {@link CatalogCursors} la mecanica del transporte —{@link Cursores}— y
 * nada mas. El par que envuelve es otro: alli es cuando se publico la prenda, aqui es
 * cuando la persona la guardo, que es el orden que pide el criterio 11.
 *
 * <p>Que los dos tipos sean distintos no es ceremonia: el cursor de un listado no lleva a
 * ninguna parte en el otro, y con un tipo comun ese error se descubriria en produccion,
 * como un tramo raro, en vez de en la compilacion.
 */
public final class FavoriteCursors {

    private FavoriteCursors() {}

    public static @Nullable String texto(@Nullable FavoriteCursor cursor) {
        return cursor == null
                ? null
                : Cursores.texto(cursor.marcadoEn(), cursor.id().value());
    }

    /**
     * @throws IllegalArgumentException si no es un cursor de esta lista. Sale como 400
     */
    public static @Nullable FavoriteCursor cursor(@Nullable String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }

        Cursores.Par par = Cursores.par(texto);
        return new FavoriteCursor(par.instante(), new ListingId(par.id()));
    }
}
