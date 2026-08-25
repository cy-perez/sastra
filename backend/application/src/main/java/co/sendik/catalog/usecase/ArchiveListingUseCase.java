package co.sendik.catalog.usecase;

import co.sendik.catalog.dto.SellerListingCommand;
import co.sendik.catalog.exception.ListingNotFoundException;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.model.ProductImage;
import co.sendik.catalog.port.out.ListingRepository;
import co.sendik.shared.port.out.PublicFileStore;
import java.time.Clock;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;

/**
 * El vendedor archiva su publicacion. Criterio 30.<p>Terminal: de aqui no se vuelve. Archivar es retirar para siempre, y quien se arrepiente publica de nuevo.
 */
public class ArchiveListingUseCase {

    private final ListingRepository publicaciones;
    private final PublicFileStore almacen;
    private final Clock reloj;

    public ArchiveListingUseCase(ListingRepository publicaciones, PublicFileStore almacen, Clock reloj) {
        this.publicaciones = publicaciones;
        this.almacen = almacen;
        this.reloj = reloj;
    }

    @Transactional
    public Listing execute(SellerListingCommand comando) {
        Listing actual = publicaciones
                .buscarDelDueno(comando.publicacion(), comando.vendedor())
                .orElseThrow(() -> new ListingNotFoundException(comando.publicacion()));

        Listing archivada = publicaciones.guardar(actual.archivar(Instant.now(reloj)));

        // Caso borde de la historia: «archivar un borrador borra sus tomas». Archivar es
        // terminal, asi que esas imagenes ya no las va a mostrar nadie y quedarse en el
        // almacen publico solo las deja accesibles por su direccion.
        archivada.images().stream().map(ProductImage::objectKey).forEach(almacen::borrar);
        return archivada;
    }
}
