package co.sendik.catalog.usecase;

import co.sendik.catalog.dto.ChangeListingShippingCommand;
import co.sendik.catalog.exception.ListingNotFoundException;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.port.out.ListingRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cambia solo el peso y las medidas de envio. Criterio 28 y RN-062.
 *
 * <p>Caso de uso propio por lo mismo que el del precio: no pasa por moderacion y la
 * publicacion sigue visible, que es la consecuencia contraria a la de editar el
 * contenido. Lo que un moderador aprobo es como es el producto, no cuanto pesa la caja.
 */
public class ChangeListingShippingUseCase {

    private final ListingRepository publicaciones;
    private final Clock reloj;

    public ChangeListingShippingUseCase(ListingRepository publicaciones, Clock reloj) {
        this.publicaciones = publicaciones;
        this.reloj = reloj;
    }

    @Transactional
    public Listing execute(ChangeListingShippingCommand comando) {
        Listing actual = publicaciones
                .buscarDelDueno(comando.publicacion(), comando.vendedor())
                .orElseThrow(() -> new ListingNotFoundException(comando.publicacion()));

        return publicaciones.guardar(actual.cambiarEnvio(comando.envio(), Instant.now(reloj)));
    }
}
