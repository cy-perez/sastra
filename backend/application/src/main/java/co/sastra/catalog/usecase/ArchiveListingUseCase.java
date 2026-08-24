package co.sastra.catalog.usecase;

import co.sastra.catalog.dto.SellerListingCommand;
import co.sastra.catalog.model.Listing;
import co.sastra.catalog.port.out.ListingRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;

/**
 * El vendedor archiva su publicacion. Criterio 30.<p>Terminal: de aqui no se vuelve. Archivar es retirar para siempre, y quien se arrepiente publica de nuevo.
 */
public class ArchiveListingUseCase {

    private final ListingRepository publicaciones;
    private final Clock reloj;

    public ArchiveListingUseCase(ListingRepository publicaciones, Clock reloj) {
        this.publicaciones = publicaciones;
        this.reloj = reloj;
    }

    @Transactional
    public Listing execute(SellerListingCommand comando) {
        Listing actual = ListingAccess.deVendedor(publicaciones, comando.publicacion(), comando.vendedor());

        return publicaciones.guardar(actual.archivar(Instant.now(reloj)));
    }
}
