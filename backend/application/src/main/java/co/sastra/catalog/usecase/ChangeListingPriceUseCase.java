package co.sastra.catalog.usecase;

import co.sastra.catalog.dto.ChangeListingPriceCommand;
import co.sastra.catalog.exception.ListingNotFoundException;
import co.sastra.catalog.model.Listing;
import co.sastra.catalog.port.out.ListingRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cambia solo el precio. Criterio 28, RN-030 y RN-062.
 *
 * <p>No pasa por moderacion y la publicacion sigue visible: se modera lo que describe
 * el producto, no lo que cuesta. Caso de uso propio y no un parametro de la edicion,
 * precisamente porque su consecuencia sobre el estado es la contraria.
 */
public class ChangeListingPriceUseCase {

    private final ListingRepository publicaciones;
    private final Clock reloj;

    public ChangeListingPriceUseCase(ListingRepository publicaciones, Clock reloj) {
        this.publicaciones = publicaciones;
        this.reloj = reloj;
    }

    @Transactional
    public Listing execute(ChangeListingPriceCommand comando) {
        Listing actual = publicaciones
                .buscarDelDueno(comando.publicacion(), comando.vendedor())
                .orElseThrow(() -> new ListingNotFoundException(comando.publicacion()));

        return publicaciones.guardar(actual.cambiarPrecio(comando.precio(), Instant.now(reloj)));
    }
}
