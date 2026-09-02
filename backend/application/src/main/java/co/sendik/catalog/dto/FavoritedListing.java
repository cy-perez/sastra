package co.sendik.catalog.dto;

import co.sendik.catalog.model.Listing;
import java.time.Instant;
import java.util.Objects;

/**
 * Una publicacion guardada, con la fecha del gesto. HU-011, criterios 11 y 12.
 *
 * <p>Existe por el cursor. La lista entrega publicaciones, pero el «por donde seguir» se
 * arma con la fecha en que se guardo la ultima, y esa fecha no esta en {@link Listing}:
 * es del favorito. Sin este par, el repositorio tendria que devolver dos colecciones que
 * quien llama volveria a casar por identificador, o el cursor tendria que ordenar por algo
 * que no es lo que pide el criterio 11.
 *
 * @param publicacion la publicacion, ya con lo que la tarjeta necesita
 * @param marcadoEn cuando la guardo esta persona. No es {@code publishedAt} y no se
 *     parecen: una publicacion vieja guardada hoy va la primera
 */
public record FavoritedListing(Listing publicacion, Instant marcadoEn) {

    public FavoritedListing {
        Objects.requireNonNull(publicacion, "La publicacion es obligatoria");
        Objects.requireNonNull(marcadoEn, "La fecha en que se marco es obligatoria");
    }
}
