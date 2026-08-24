package co.sastra.catalog.port.out;

import co.sastra.catalog.model.ListingId;
import co.sastra.catalog.model.ModerationAction;
import co.sastra.catalog.model.ModeratorId;
import org.jspecify.annotations.Nullable;

/**
 * El rastro de cada decision de moderacion. RN-045: ninguna transicion se pierde.
 *
 * <p>Separado del repositorio a proposito. Guardar la publicacion y anotar quien la
 * decidio son dos cosas distintas: la primera describe como esta el catalogo ahora, la
 * segunda por que llego a estarlo, y esa segunda no se sobrescribe nunca.
 */
public interface ModerationLog {

    void registrar(
            ListingId publicacion,
            ModeratorId actor,
            ModerationAction accion,
            @Nullable String motivo,
            @Nullable String nota);
}
