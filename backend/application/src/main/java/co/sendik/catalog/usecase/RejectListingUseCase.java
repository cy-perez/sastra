package co.sendik.catalog.usecase;

import co.sendik.catalog.dto.RejectListingCommand;
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
 * El moderador rechaza con motivo de la lista cerrada. Criterios 22, 24 y 26.
 *
 * <p>Que el motivo sea obligatorio lo exige el dominio (RN-022); aqui solo se
 * comprueba quien decide. La nota viaja al vendedor y nunca lleva datos de un tercero,
 * que es una regla de redaccion y no de codigo: ningun tipo puede impedirlo.
 */
public class RejectListingUseCase {

    private final ListingRepository publicaciones;
    private final ModerationLog bitacora;
    private final ListingNotifier avisos;
    private final Clock reloj;

    public RejectListingUseCase(
            ListingRepository publicaciones, ModerationLog bitacora, ListingNotifier avisos, Clock reloj) {
        this.publicaciones = publicaciones;
        this.bitacora = bitacora;
        this.avisos = avisos;
        this.reloj = reloj;
    }

    @Transactional
    public Listing execute(RejectListingCommand comando) {
        Listing actual = publicaciones
                .buscar(comando.publicacion())
                .orElseThrow(() -> new ListingNotFoundException(comando.publicacion()));

        if (actual.laPublico(comando.moderador())) {
            throw new SelfModerationForbiddenException();
        }

        Listing rechazada = publicaciones.guardar(
                actual.rechazar(comando.moderador(), comando.motivo(), comando.nota(), Instant.now(reloj)));

        bitacora.registrar(
                rechazada.id(),
                comando.moderador(),
                ModerationAction.REJECTED,
                comando.motivo().name(),
                comando.nota());
        avisos.publicacionRechazada(rechazada, comando.nota());
        return rechazada;
    }
}
