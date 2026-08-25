package co.sendik.catalog.usecase;

import co.sendik.catalog.dto.SellerListingCommand;
import co.sendik.catalog.exception.ListingNotFoundException;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.port.out.ListingRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reanuda una publicacion pausada. Criterio 29.<p>Vuelve a verse sin pasar por moderacion, por lo mismo que pausar: entre las dos acciones no cambio nada de lo aprobado.
 */
public class ResumeListingUseCase {

    private final ListingRepository publicaciones;
    private final Clock reloj;

    public ResumeListingUseCase(ListingRepository publicaciones, Clock reloj) {
        this.publicaciones = publicaciones;
        this.reloj = reloj;
    }

    @Transactional
    public Listing execute(SellerListingCommand comando) {
        Listing actual = publicaciones
                .buscarDelDueno(comando.publicacion(), comando.vendedor())
                .orElseThrow(() -> new ListingNotFoundException(comando.publicacion()));

        return publicaciones.guardar(actual.reanudar(Instant.now(reloj)));
    }
}
