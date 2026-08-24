package co.sastra.catalog.usecase;

import co.sastra.catalog.dto.ProductData;
import co.sastra.catalog.dto.UpdateListingContentCommand;
import co.sastra.catalog.exception.UnknownCategoryException;
import co.sastra.catalog.model.Category;
import co.sastra.catalog.model.Listing;
import co.sastra.catalog.model.Product;
import co.sastra.catalog.port.out.Categories;
import co.sastra.catalog.port.out.ListingRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cambia lo que describe el producto. Criterio 27, RN-062.
 *
 * <p>Si la publicacion estaba viva, vuelve a {@code PENDING_REVIEW} y deja de verse.
 * Esa decision es del dominio: aqui solo se le entrega el producto nuevo.
 *
 * <p>La categoria se vuelve a comprobar aunque no cambie, porque el producto nuevo trae
 * condicion y talla y las dos dependen de ella. Es tambien lo que impide el caso borde
 * de mover una publicacion de moda con condicion usada a una categoria de tecnologia:
 * {@code Product.crear} lo rechaza con RN-064 en vez de corregir la condicion por su
 * cuenta.
 */
public class UpdateListingContentUseCase {

    private final ListingRepository publicaciones;
    private final Categories categorias;
    private final Clock reloj;

    public UpdateListingContentUseCase(ListingRepository publicaciones, Categories categorias, Clock reloj) {
        this.publicaciones = publicaciones;
        this.categorias = categorias;
        this.reloj = reloj;
    }

    @Transactional
    public Listing execute(UpdateListingContentCommand comando) {
        Listing actual = ListingAccess.deVendedor(publicaciones, comando.publicacion(), comando.vendedor());

        ProductData datos = comando.datos();
        Category categoria = categorias
                .buscar(datos.categoria())
                .filter(Category::admitePublicaciones)
                .orElseThrow(() -> new UnknownCategoryException(datos.categoria()));

        Product editado = Product.crear(
                actual.product().id(),
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

        return publicaciones.guardar(actual.editarContenido(editado, Instant.now(reloj)));
    }
}
