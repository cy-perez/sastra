package co.sastra.catalog.usecase;

import co.sastra.catalog.dto.SellerListingCommand;
import co.sastra.catalog.exception.SellerNotEligibleException;
import co.sastra.catalog.exception.UnknownCategoryException;
import co.sastra.catalog.model.Category;
import co.sastra.catalog.model.Listing;
import co.sastra.catalog.port.out.Categories;
import co.sastra.catalog.port.out.ListingRepository;
import co.sastra.catalog.port.out.SellerEligibility;
import java.time.Clock;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;

/**
 * Envia el borrador a revision. Criterios 6, 17 y 19.
 *
 * <p>Tres comprobaciones, y cada una donde puede estar:
 *
 * <ul>
 *   <li>Que el vendedor siga pudiendo publicar. Es el caso borde de RN-013: quien
 *       pierde el sello con borradores abiertos los conserva, y no los puede enviar.
 *   <li>Que las medidas del grupo esten completas. Exige la categoria, que el dominio
 *       de la publicacion no conoce.
 *   <li>Que esten las tomas y las canonicas. Eso si lo sabe la publicacion, y por eso
 *       lo comprueba ella dentro de {@code enviarARevision}.
 * </ul>
 */
public class SubmitListingForReviewUseCase {

    private final ListingRepository publicaciones;
    private final Categories categorias;
    private final SellerEligibility elegibilidad;
    private final Clock reloj;

    public SubmitListingForReviewUseCase(
            ListingRepository publicaciones, Categories categorias, SellerEligibility elegibilidad, Clock reloj) {
        this.publicaciones = publicaciones;
        this.categorias = categorias;
        this.elegibilidad = elegibilidad;
        this.reloj = reloj;
    }

    @Transactional
    public Listing execute(SellerListingCommand comando) {
        if (!elegibilidad.puedePublicar(comando.vendedor())) {
            throw new SellerNotEligibleException();
        }

        Listing actual = ListingAccess.deVendedor(publicaciones, comando.publicacion(), comando.vendedor());

        Category categoria = categorias
                .buscar(actual.product().categoryId())
                .orElseThrow(() -> new UnknownCategoryException(actual.product().categoryId()));
        actual.product().exigirCompletoPara(categoria);

        return publicaciones.guardar(actual.enviarARevision(Instant.now(reloj)));
    }
}
