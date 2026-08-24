package co.sastra.catalog.usecase;

import co.sastra.catalog.dto.TakeDownListingCommand;
import co.sastra.catalog.exception.SelfModerationForbiddenException;
import co.sastra.catalog.model.Listing;
import co.sastra.catalog.model.ModerationAction;
import co.sastra.catalog.port.out.ListingNotifier;
import co.sastra.catalog.port.out.ListingRepository;
import co.sastra.catalog.port.out.ModerationLog;
import java.time.Clock;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;

/**
 * El moderador baja una publicacion ya visible que infringe RN-024. Criterio 31.
 *
 * <p>Es lo que hace que aprobar no sea irreversible. Sin esto, la unica salida a una
 * replica detectada tarde seria la base de datos.
 *
 * <p>Archivar es terminal: el vendedor no la puede reactivar. Corregir y volver a
 * intentarlo es publicar de nuevo, que es lo correcto cuando lo que se retiro era algo
 * que no se podia vender.
 */
public class TakeDownListingUseCase {

    private final ListingRepository publicaciones;
    private final ModerationLog bitacora;
    private final ListingNotifier avisos;
    private final Clock reloj;

    public TakeDownListingUseCase(
            ListingRepository publicaciones, ModerationLog bitacora, ListingNotifier avisos, Clock reloj) {
        this.publicaciones = publicaciones;
        this.bitacora = bitacora;
        this.avisos = avisos;
        this.reloj = reloj;
    }

    @Transactional
    public Listing execute(TakeDownListingCommand comando) {
        Listing actual = ListingAccess.cualquiera(publicaciones, comando.publicacion());

        if (actual.sellerId().value().equals(comando.moderador().value())) {
            throw new SelfModerationForbiddenException();
        }

        Listing retirada = publicaciones.guardar(actual.archivar(Instant.now(reloj)));

        bitacora.registrar(
                retirada.id(),
                comando.moderador(),
                ModerationAction.ARCHIVED,
                comando.motivo().name(),
                comando.nota());
        avisos.publicacionRetirada(retirada, comando.nota());
        return retirada;
    }
}
