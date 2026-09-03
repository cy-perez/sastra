package co.sendik.catalog.dto;

import co.sendik.catalog.model.BuyerId;
import co.sendik.catalog.model.ListingId;
import java.util.Objects;

/**
 * Marcar o quitar un favorito. HU-011, criterios 2, 3 y 4.
 *
 * <p>El mismo comando para las dos operaciones porque la pregunta es la misma —quien, y
 * sobre cual— y lo que cambia es el verbo, que es el caso de uso. Dos records identicos
 * con distinto nombre no dirian nada mas.
 *
 * <p><strong>{@code quien} sale del token y jamas de la peticion.</strong> Es la regla de
 * backend/CLAUDE.md, y aqui protege de lo evidente: con un identificador que viaje en el
 * cuerpo, cualquiera llenaria la lista de favoritos de otra persona.
 */
public record FavoriteCommand(BuyerId quien, ListingId publicacion) {

    public FavoriteCommand {
        Objects.requireNonNull(quien, "Quien marca es obligatorio");
        Objects.requireNonNull(publicacion, "La publicacion es obligatoria");
    }
}
