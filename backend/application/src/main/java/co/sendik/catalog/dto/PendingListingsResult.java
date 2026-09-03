package co.sendik.catalog.dto;

import co.sendik.catalog.model.Listing;
import java.util.List;

/**
 * Una pagina de la cola del moderador, y si detras queda algo mas. HU-008.
 *
 * <p>Es la forma que ya tiene la cola de verificaciones, y por el mismo motivo: «hay mas»
 * no se puede deducir de que la pagina venga llena. La deduccion falla justo cuando el
 * total es multiplo exacto del tamano, y entonces la pantalla ofrece un «Siguiente» que
 * lleva a una pagina vacia; quien revisa pulsa, no encuentra nada, y no puede saber si la
 * cola se acabo o si algo se rompio.
 *
 * <p>Se resuelve preguntando si queda alguna despues de esta pagina. Contar es la otra
 * forma, y obliga a recorrerlas todas para responder un si o un no.
 *
 * @param items las publicaciones de esta pagina
 * @param hayMas si detras de esta pagina queda al menos una
 */
public record PendingListingsResult(List<Listing> items, boolean hayMas) {

    public PendingListingsResult {
        items = List.copyOf(items);
    }
}
