package co.sendik.catalog.usecase;

import co.sendik.catalog.dto.SellerListingCommand;
import co.sendik.catalog.exception.ListingNotFoundException;
import co.sendik.catalog.exception.SellerNotEligibleException;
import co.sendik.catalog.exception.UnknownCategoryException;
import co.sendik.catalog.model.Category;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.port.out.Categories;
import co.sendik.catalog.port.out.ListingRepository;
import co.sendik.catalog.port.out.ModerationLog;
import co.sendik.catalog.port.out.SellerEligibility;
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
 *
 * <p><strong>Anota el envio en la bitacora</strong> desde HU-013. Es la primera entrada del
 * rastro que ve el vendedor, y sin ella empieza a media frase: se veria que la rechazaron
 * sin que se vea nunca que la habia mandado.
 */
public class SubmitListingForReviewUseCase {

    private final ListingRepository publicaciones;
    private final Categories categorias;
    private final SellerEligibility elegibilidad;
    private final ModerationLog bitacora;
    private final Clock reloj;

    public SubmitListingForReviewUseCase(
            ListingRepository publicaciones,
            Categories categorias,
            SellerEligibility elegibilidad,
            ModerationLog bitacora,
            Clock reloj) {
        this.publicaciones = publicaciones;
        this.categorias = categorias;
        this.elegibilidad = elegibilidad;
        this.bitacora = bitacora;
        this.reloj = reloj;
    }

    @Transactional
    public Listing execute(SellerListingCommand comando) {
        if (!elegibilidad.puedePublicar(comando.vendedor())) {
            throw new SellerNotEligibleException();
        }

        Listing actual = publicaciones
                .buscarDelDueno(comando.publicacion(), comando.vendedor())
                .orElseThrow(() -> new ListingNotFoundException(comando.publicacion()));

        Category categoria = categorias
                .buscar(actual.product().categoryId())
                .orElseThrow(() -> new UnknownCategoryException(actual.product().categoryId()));
        actual.product().exigirCompletoPara(categoria);

        // El mismo instante para el sello del dominio y para la fila del rastro. Con dos
        // llamadas al reloj, el evento y el `submitted_at` de la publicacion contarian
        // momentos distintos del mismo envio.
        Instant ahora = Instant.now(reloj);
        Listing enviada = publicaciones.guardar(actual.enviarARevision(ahora));

        bitacora.registrarEnvio(enviada.id(), comando.vendedor(), ahora);
        return enviada;
    }
}
