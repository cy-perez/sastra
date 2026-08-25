package co.sastra.catalog.usecase;

import co.sastra.catalog.dto.SellerListingCommand;
import co.sastra.catalog.exception.ListingNotFoundException;
import co.sastra.catalog.model.Listing;
import co.sastra.catalog.port.out.ListingRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pausa una publicacion viva. Criterio 29.<p>Deja de verse y no pasa por moderacion, porque pausar no cambia nada de lo que un moderador aprobo.
 */
public class PauseListingUseCase {

    private final ListingRepository publicaciones;
    private final Clock reloj;

    public PauseListingUseCase(ListingRepository publicaciones, Clock reloj) {
        this.publicaciones = publicaciones;
        this.reloj = reloj;
    }

    @Transactional
    public Listing execute(SellerListingCommand comando) {
        Listing actual = publicaciones
                .buscarDelDueno(comando.publicacion(), comando.vendedor())
                .orElseThrow(() -> new ListingNotFoundException(comando.publicacion()));

        return publicaciones.guardar(actual.pausar(Instant.now(reloj)));
    }
}
