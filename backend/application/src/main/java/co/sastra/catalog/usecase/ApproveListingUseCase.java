package co.sastra.catalog.usecase;

import co.sastra.catalog.dto.ApproveListingCommand;
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
 * El moderador aprueba y la publicacion se vuelve visible. Criterios 21, 24 y 26.
 *
 * <p>RN-063 se comprueba aqui y no en el dominio porque exige comparar dos personas, y
 * una publicacion solo se conoce a si misma. Se comprueba <strong>antes</strong> de
 * mover el estado: si se hiciera despues, un fallo al registrar dejaria publicado algo
 * que su propio dueno aprobo.
 *
 * <p>El aviso al vendedor va despues de guardar. Si el correo falla, la publicacion ya
 * esta aprobada y el fallo se puede reintentar; al reves, se habria avisado de algo que
 * no ocurrio.
 */
public class ApproveListingUseCase {

    private final ListingRepository publicaciones;
    private final ModerationLog bitacora;
    private final ListingNotifier avisos;
    private final Clock reloj;

    public ApproveListingUseCase(
            ListingRepository publicaciones, ModerationLog bitacora, ListingNotifier avisos, Clock reloj) {
        this.publicaciones = publicaciones;
        this.bitacora = bitacora;
        this.avisos = avisos;
        this.reloj = reloj;
    }

    @Transactional
    public Listing execute(ApproveListingCommand comando) {
        Listing actual = ListingAccess.cualquiera(publicaciones, comando.publicacion());
        exigirQueNoSeaSuya(actual, comando);

        Listing aprobada = publicaciones.guardar(actual.aprobar(comando.moderador(), Instant.now(reloj)));

        bitacora.registrar(aprobada.id(), comando.moderador(), ModerationAction.APPROVED, null, null);
        avisos.publicacionAprobada(aprobada);
        return aprobada;
    }

    /** RN-063. */
    private static void exigirQueNoSeaSuya(Listing publicacion, ApproveListingCommand comando) {
        if (publicacion.sellerId().value().equals(comando.moderador().value())) {
            throw new SelfModerationForbiddenException();
        }
    }
}
