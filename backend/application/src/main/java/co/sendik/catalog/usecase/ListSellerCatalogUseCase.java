package co.sendik.catalog.usecase;

import co.sendik.catalog.dto.CatalogPage;
import co.sendik.catalog.dto.ListSellerCatalogQuery;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.port.out.ListingRepository;
import java.util.List;

/**
 * Lo que un vendedor tiene a la venta, para cualquiera. HU-009, criterios 18 y 20.
 *
 * <p><strong>No es {@code buscarDelVendedor}, y confundirlos seria grave.</strong> Aquel
 * es el panel del propio vendedor: trae sus siete estados, incluidos los borradores y lo
 * rechazado con su motivo. Este es el escaparate y aplica RN-068 igual que el catalogo
 * general. Son dos listas del mismo vendedor y solo una es publica.
 *
 * <p>Por eso pasa por el mismo metodo del repositorio que el catalogo, con el filtro de
 * vendedor en vez del de categoria: la condicion de «publicada» se escribe una sola vez y
 * no hay una segunda consulta que pueda olvidarla.
 */
public class ListSellerCatalogUseCase {

    private final ListingRepository publicaciones;

    public ListSellerCatalogUseCase(ListingRepository publicaciones) {
        this.publicaciones = publicaciones;
    }

    public CatalogPage execute(ListSellerCatalogQuery consulta) {
        List<Listing> traidas =
                publicaciones.publicadasDelVendedor(consulta.vendedor(), consulta.desde(), consulta.limite() + 1);

        return ListCatalogUseCase.armar(traidas, consulta.limite());
    }
}
