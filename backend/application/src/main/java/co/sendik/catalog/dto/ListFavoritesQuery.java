package co.sendik.catalog.dto;

import co.sendik.catalog.model.BuyerId;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Un tramo de la lista propia de favoritos. HU-011, criterios 11 y 12.
 *
 * <p>El tope vive aqui y no en el controlador, por lo mismo que en el catalogo publico:
 * escrito en el borde protegeria esa ruta y ninguna otra que use el mismo caso de uso. Un
 * limite por encima del tope se rechaza y no se recorta en silencio.
 *
 * <p>Se reusan los topes de {@link ListCatalogQuery} en vez de declarar otros: es la misma
 * rejilla con las mismas tarjetas, y dos numeros distintos para la misma pantalla serian
 * dos numeros que alguien tendria que mantener iguales.
 *
 * <p><strong>{@code quien} no es opcional</strong>, al contrario que en el catalogo: alli
 * la lista es la misma para todo el mundo y por eso el caso de uso no recibe quien
 * pregunta; aqui la lista *es* de alguien, y sin ese dato la consulta no significa nada.
 *
 * @param quien de quien es la lista. Sale del token
 * @param desde por donde seguir, o nulo para el primer tramo
 * @param limite cuantas se piden como maximo
 */
public record ListFavoritesQuery(BuyerId quien, @Nullable FavoriteCursor desde, int limite) {

    public ListFavoritesQuery {
        Objects.requireNonNull(quien, "De quien es la lista es obligatorio");

        if (limite < 1 || limite > ListCatalogQuery.LIMITE_MAXIMO) {
            throw new IllegalArgumentException(
                    "El limite va entre 1 y " + ListCatalogQuery.LIMITE_MAXIMO + ", y llego " + limite);
        }
    }
}
