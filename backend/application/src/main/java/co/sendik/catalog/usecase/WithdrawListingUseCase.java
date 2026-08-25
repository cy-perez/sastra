package co.sendik.catalog.usecase;

import co.sendik.catalog.dto.SellerListingCommand;
import co.sendik.catalog.exception.ListingNotFoundException;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.port.out.ListingRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;

/**
 * Retira la solicitud antes de que se decida. Criterio 20.<p>Si el moderador ya decidio, el dominio responde con un conflicto y la decision se mantiene: no se le quita a nadie una publicacion ya aprobada por llegar tarde.
 */
public class WithdrawListingUseCase {

    private final ListingRepository publicaciones;
    private final Clock reloj;

    public WithdrawListingUseCase(ListingRepository publicaciones, Clock reloj) {
        this.publicaciones = publicaciones;
        this.reloj = reloj;
    }

    @Transactional
    public Listing execute(SellerListingCommand comando) {
        Listing actual = publicaciones
                .buscarDelDueno(comando.publicacion(), comando.vendedor())
                .orElseThrow(() -> new ListingNotFoundException(comando.publicacion()));

        return publicaciones.guardar(actual.retirarDeRevision(Instant.now(reloj)));
    }
}
