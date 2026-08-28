package co.sendik.catalog.dto;

import co.sendik.catalog.model.Listing;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Un tramo del catalogo, con por donde sigue. HU-009, criterio 3.
 *
 * <p>{@code hayMas} no se deduce de que el tramo venga lleno: un catalogo con exactamente
 * veinticuatro publicaciones devolveria veinticuatro y un cursor que no lleva a ninguna
 * parte, y quien lo siguiera recibiria un tramo vacio. Se sabe pidiendo uno mas de los que
 * se van a entregar y mirando si vino; el sobrante se descarta y nunca sale de aqui.
 *
 * <p>Cuando no hay mas, {@code siguiente} es nulo. Las dos cosas se dicen por separado
 * porque el cliente las usa para cosas distintas: una decide si pintar «ver mas», la otra
 * es lo que manda al pulsarlo.
 */
public record CatalogPage(List<Listing> items, @Nullable CatalogCursor siguiente, boolean hayMas) {

    public CatalogPage {
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
    public static CatalogPage ultima(List<Listing> items) {
        return new CatalogPage(items, null, false);
    }
}
