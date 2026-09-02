package co.sendik.catalog.rest.mapper;

import co.sendik.catalog.dto.CatalogCursor;
import co.sendik.catalog.model.ListingId;
import org.jspecify.annotations.Nullable;

/**
 * El cursor del catalogo, de par de valores a cadena opaca y vuelta. HU-009, criterio 3.
 *
 * <p>La mecanica del transporte —base64 de URL, el JSON de dentro y el rechazo de lo que no
 * se entiende— vive en {@link Cursores} desde HU-011, porque la lista de favoritos necesita
 * exactamente la misma y copiarla habria dejado dos sitios donde dejar de rechazar un
 * cursor corrupto. Lo que se queda aqui es lo unico propio del catalogo: que ese par es una
 * fecha de publicacion y una publicacion.
 *
 * <p><strong>El tipo no se comparte y eso es lo importante.</strong> {@link CatalogCursor}
 * y {@code FavoriteCursor} siguen siendo records distintos, asi que el compilador impide
 * que el cursor de un listado sirva en el otro; si lo permitiera, pasar el de aqui a la
 * lista de favoritos devolveria un tramo arbitrario en vez de un error.
 */
public final class CatalogCursors {

    private CatalogCursors() {}

    public static @Nullable String texto(@Nullable CatalogCursor cursor) {
        return cursor == null
                ? null
                : Cursores.texto(cursor.publicadaEn(), cursor.id().value());
    }

    /**
     * @throws IllegalArgumentException si no es un cursor de este listado. Sale como 400,
     *     que es lo que el manejador ya hace con esta excepcion
     */
    public static @Nullable CatalogCursor cursor(@Nullable String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }

        Cursores.Par par = Cursores.par(texto);
        return new CatalogCursor(par.instante(), new ListingId(par.id()));
    }
}
