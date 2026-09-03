package co.sendik.catalog.dto;

import co.sendik.catalog.model.Listing;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Un tramo de la lista de favoritos. HU-011, criterio 12.
 *
 * <p>Misma forma y mismas invariantes que {@link CatalogPage}, y tipo aparte por lo mismo
 * que el cursor: el «por donde seguir» de esta lista no es el de aquella, y con un solo
 * tipo el compilador dejaria pasar que se cruzaran.
 *
 * <p>{@code hayMas} no se deduce de que el tramo venga lleno. Se sabe pidiendo uno mas de
 * los que se van a entregar y mirando si vino; el sobrante nunca sale de aqui.
 */
public record FavoritePage(List<Listing> items, @Nullable FavoriteCursor siguiente, boolean hayMas) {

    public FavoritePage {
        Objects.requireNonNull(items, "Los elementos son obligatorios");
        items = List.copyOf(items);

        if (hayMas && siguiente == null) {
            throw new IllegalArgumentException("Si hay mas tiene que haber por donde seguir");
        }
        if (!hayMas && siguiente != null) {
            throw new IllegalArgumentException("Si no hay mas no puede haber por donde seguir");
        }
    }

    /** El tramo final: lo que haya y nada despues. */
    public static FavoritePage ultima(List<Listing> items) {
        return new FavoritePage(items, null, false);
    }
}
