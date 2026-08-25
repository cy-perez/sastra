package co.sendik.catalog.usecase;

import co.sendik.catalog.dto.ListSellerListingsQuery;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.port.out.ListingRepository;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * Las publicaciones del vendedor, para que llegue a sus borradores.
 *
 * <p>No es el panel del vendedor, que es otra historia: es lo minimo para que un
 * borrador guardado a medias se pueda volver a abrir. Sin esto, un borrador que no se
 * termina en la misma sesion se pierde de vista.
 */
public class ListSellerListingsUseCase {

    private final ListingRepository publicaciones;

    public ListSellerListingsUseCase(ListingRepository publicaciones) {
        this.publicaciones = publicaciones;
    }

    @Transactional(readOnly = true)
    public List<Listing> execute(ListSellerListingsQuery consulta) {
        return publicaciones.buscarDelVendedor(consulta.vendedor(), consulta.pagina(), consulta.tamano());
    }
}
