package co.sastra.catalog.usecase;

import co.sastra.catalog.dto.RemoveListingImageCommand;
import co.sastra.catalog.exception.ListingNotFoundException;
import co.sastra.catalog.model.Listing;
import co.sastra.catalog.model.ProductImage;
import co.sastra.catalog.port.out.ListingRepository;
import co.sastra.shared.port.out.PublicFileStore;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * Borra una toma o una imagen de referencia.
 *
 * <p>El archivo se borra despues de guardar la fila, no antes. {@code borrar} no falla
 * nunca por contrato del puerto, asi que si el almacen esta caido lo que queda es un
 * archivo suelto que se puede barrer; al reves, quedaria una fila apuntando a algo que
 * ya no existe.
 */
public class RemoveListingImageUseCase {

    private final ListingRepository publicaciones;
    private final PublicFileStore almacen;
    private final Clock reloj;

    public RemoveListingImageUseCase(ListingRepository publicaciones, PublicFileStore almacen, Clock reloj) {
        this.publicaciones = publicaciones;
        this.almacen = almacen;
        this.reloj = reloj;
    }

    @Transactional
    public Listing execute(RemoveListingImageCommand comando) {
        Listing actual = publicaciones
                .buscarDelDueno(comando.publicacion(), comando.vendedor())
                .orElseThrow(() -> new ListingNotFoundException(comando.publicacion()));

        Optional<ProductImage> borrada = actual.images().stream()
                .filter(imagen -> imagen.id().equals(comando.imagen()))
                .findFirst();

        Listing guardada = publicaciones.guardar(actual.sinImagen(comando.imagen(), Instant.now(reloj)));

        borrada.ifPresent(imagen -> almacen.borrar(imagen.objectKey()));
        return guardada;
    }
}
