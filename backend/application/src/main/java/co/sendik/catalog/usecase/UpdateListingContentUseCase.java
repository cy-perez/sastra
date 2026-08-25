package co.sendik.catalog.usecase;

import co.sendik.catalog.dto.ProductData;
import co.sendik.catalog.dto.UpdateListingContentCommand;
import co.sendik.catalog.exception.ListingNotFoundException;
import co.sendik.catalog.exception.SellerNotEligibleException;
import co.sendik.catalog.exception.UnknownCategoryException;
import co.sendik.catalog.model.Category;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.model.Product;
import co.sendik.catalog.port.out.Categories;
import co.sendik.catalog.port.out.ListingRepository;
import co.sendik.catalog.port.out.SellerEligibility;
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
    private final SellerEligibility elegibilidad;
    private final Clock reloj;

    public UpdateListingContentUseCase(
            ListingRepository publicaciones, Categories categorias, SellerEligibility elegibilidad, Clock reloj) {
        this.publicaciones = publicaciones;
        this.categorias = categorias;
        this.elegibilidad = elegibilidad;
        this.reloj = reloj;
    }

    @Transactional
    public Listing execute(UpdateListingContentCommand comando) {
        // RN-011 y RN-013. Sin esto, quien perdio el sello no puede enviar un borrador
        // pero si reescribir una publicacion entera y devolverla a la cola de
        // moderacion: la unica puerta del catalogo quedaba con una hoja sin cerradura.
        if (!elegibilidad.puedePublicar(comando.vendedor())) {
            throw new SellerNotEligibleException();
        }

        Listing actual = publicaciones
                .buscarDelDueno(comando.publicacion(), comando.vendedor())
                .orElseThrow(() -> new ListingNotFoundException(comando.publicacion()));

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
