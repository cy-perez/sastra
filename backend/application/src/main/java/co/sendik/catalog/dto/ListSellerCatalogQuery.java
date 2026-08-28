package co.sendik.catalog.dto;

import co.sendik.catalog.model.SellerId;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Un tramo de lo que vende alguien, visto desde fuera. HU-009.
 *
 * <p>Mismo tope y mismo cursor que {@link ListCatalogQuery}: es la misma lista filtrada de
 * otra manera, y dos topes distintos para dos listas iguales acaban divergiendo.
 *
 * @param vendedor de quien es el escaparate
 * @param desde por donde seguir, o nulo para el primer tramo
 * @param limite cuantas se piden como maximo
 */
public record ListSellerCatalogQuery(
        SellerId vendedor, @Nullable CatalogCursor desde, int limite) {

    public ListSellerCatalogQuery {
        Objects.requireNonNull(vendedor, "El vendedor es obligatorio");

        if (limite < 1 || limite > ListCatalogQuery.LIMITE_MAXIMO) {
            throw new IllegalArgumentException(
                    "El limite va entre 1 y " + ListCatalogQuery.LIMITE_MAXIMO + ", y llego " + limite);
        }
    }
}
