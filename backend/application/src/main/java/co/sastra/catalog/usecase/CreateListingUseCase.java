package co.sastra.catalog.usecase;

import co.sastra.catalog.dto.CreateListingCommand;
import co.sastra.catalog.dto.ProductData;
import co.sastra.catalog.exception.SellerNotEligibleException;
import co.sastra.catalog.exception.UnknownCategoryException;
import co.sastra.catalog.model.Category;
import co.sastra.catalog.model.Listing;
import co.sastra.catalog.model.ListingId;
import co.sastra.catalog.model.Product;
import co.sastra.catalog.model.ProductId;
import co.sastra.catalog.port.out.Categories;
import co.sastra.catalog.port.out.ListingRepository;
import co.sastra.catalog.port.out.SellerEligibility;
import java.time.Clock;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea el borrador. Criterios 1, 2 y 4.
 *
 * <p>Las tres comprobaciones que solo se pueden hacer aqui, porque exigen algo que el
 * dominio no ve: que el vendedor pueda publicar hoy (RN-011, RN-013), que la categoria
 * exista y admita publicaciones, y que el producto encaje con esa categoria (RN-064).
 *
 * <p>El orden importa. Se pregunta primero si puede publicar, antes de tocar el arbol:
 * a quien no puede publicar no se le confirma que categorias existen.
 */
public class CreateListingUseCase {

    private final ListingRepository publicaciones;
    private final Categories categorias;
    private final SellerEligibility elegibilidad;
    private final Clock reloj;

    public CreateListingUseCase(
            ListingRepository publicaciones, Categories categorias, SellerEligibility elegibilidad, Clock reloj) {
        this.publicaciones = publicaciones;
        this.categorias = categorias;
        this.elegibilidad = elegibilidad;
        this.reloj = reloj;
    }

    @Transactional
    public Listing execute(CreateListingCommand comando) {
        if (!elegibilidad.puedePublicar(comando.vendedor())) {
            throw new SellerNotEligibleException();
        }

        ProductData datos = comando.datos();
        Category categoria = categorias
                .buscar(datos.categoria())
                .filter(Category::admitePublicaciones)
                .orElseThrow(() -> new UnknownCategoryException(datos.categoria()));

        Product producto = Product.crear(
                ProductId.nuevo(),
                comando.vendedor(),
                categoria,
                datos.titulo(),
                datos.descripcion(),
                datos.marca(),
                datos.condicion(),
                datos.talla(),
                datos.medidas(),
                datos.color(),
                datos.precio(),
                datos.envio(),
                datos.sellado(),
                datos.garantia());

        Instant ahora = Instant.now(reloj);
        return publicaciones.guardar(Listing.crearBorrador(ListingId.nuevo(), producto, ahora));
    }
}
