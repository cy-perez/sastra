package co.sendik.catalog.dto;

import co.sendik.catalog.model.ListingId;
import java.time.Instant;
import java.util.Objects;

/**
 * Por donde sigue la lista de favoritos. HU-011, criterio 12.
 *
 * <p><strong>Propio y no {@link CatalogCursor}, aunque los dos lleven un instante y un
 * identificador.</strong> Aquel ordena por cuando se publico la prenda; este por cuando la
 * persona la guardo, que es lo que pide el criterio 11: el orden es el del gesto. Son dos
 * preguntas distintas que casualmente tienen la misma forma, y compartir el tipo dejaria
 * que un cursor de un listado sirviera en el otro, devolviendo un tramo arbitrario en vez
 * de un error.
 *
 * <p>Lleva los dos valores por los que ordena y no solo la fecha, por lo mismo que el del
 * catalogo: dos publicaciones guardadas en el mismo instante —dos toques seguidos, o una
 * prueba con reloj fijo— dejarian un par en orden indefinido, y un tramo que empieza donde
 * el anterior creia haber terminado se salta o repite elementos.
 *
 * <p>No sabe nada de base64: eso es transporte y vive en el borde.
 *
 * @param marcadoEn cuando se guardo el ultimo elemento entregado
 * @param id cual era, para desempatar
 */
public record FavoriteCursor(Instant marcadoEn, ListingId id) {

    public FavoriteCursor {
        Objects.requireNonNull(marcadoEn, "El instante en que se marco es obligatorio");
        Objects.requireNonNull(id, "El identificador es obligatorio");
    }
}
