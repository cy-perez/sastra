package co.sendik.catalog.usecase;

import co.sendik.catalog.dto.ApproveListingCommand;
import co.sendik.catalog.exception.ListingNotFoundException;
import co.sendik.catalog.exception.SelfModerationForbiddenException;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.model.ModerationAction;
import co.sendik.catalog.port.out.ListingNotifier;
import co.sendik.catalog.port.out.ListingRepository;
import co.sendik.catalog.port.out.ModerationLog;
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
        Listing actual = publicaciones
                .buscar(comando.publicacion())
                .orElseThrow(() -> new ListingNotFoundException(comando.publicacion()));
        if (actual.laPublico(comando.moderador())) {
            throw new SelfModerationForbiddenException();
        }

        // Un solo instante para la publicacion y para el rastro: los dos cuentan el mismo
        // momento o no cuentan lo mismo.
        Instant ahora = Instant.now(reloj);
        Listing aprobada = publicaciones.guardar(actual.aprobar(comando.moderador(), ahora));

        bitacora.registrar(aprobada.id(), comando.moderador(), ModerationAction.APPROVED, null, null, ahora);
        avisos.publicacionAprobada(aprobada);
        return aprobada;
    }
}
