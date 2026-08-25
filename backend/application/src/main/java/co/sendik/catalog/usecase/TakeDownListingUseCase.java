package co.sendik.catalog.usecase;

import co.sendik.catalog.dto.TakeDownListingCommand;
import co.sendik.catalog.exception.InvalidListingTransitionException;
import co.sendik.catalog.exception.ListingNotFoundException;
import co.sendik.catalog.exception.SelfModerationForbiddenException;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.model.ListingStatus;
import co.sendik.catalog.model.ModerationAction;
import co.sendik.catalog.model.ProductImage;
import co.sendik.catalog.port.out.ListingNotifier;
import co.sendik.catalog.port.out.ListingRepository;
import co.sendik.catalog.port.out.ModerationLog;
import co.sendik.shared.port.out.PublicFileStore;
import java.time.Clock;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;

/**
 * El moderador baja una publicacion ya visible que infringe RN-024. Criterio 31.
 *
 * <p>Es lo que hace que aprobar no sea irreversible. Sin esto, la unica salida a una
 * replica detectada tarde seria la base de datos.
 *
 * <p>Archivar es terminal: el vendedor no la puede reactivar. Corregir y volver a
 * intentarlo es publicar de nuevo, que es lo correcto cuando lo que se retiro era algo
 * que no se podia vender.
 */
public class TakeDownListingUseCase {

    private final ListingRepository publicaciones;
    private final ModerationLog bitacora;
    private final ListingNotifier avisos;
    private final PublicFileStore almacen;
    private final Clock reloj;

    public TakeDownListingUseCase(
            ListingRepository publicaciones,
            ModerationLog bitacora,
            ListingNotifier avisos,
            PublicFileStore almacen,
            Clock reloj) {
        this.publicaciones = publicaciones;
        this.bitacora = bitacora;
        this.avisos = avisos;
        this.almacen = almacen;
        this.reloj = reloj;
    }

    @Transactional
    public Listing execute(TakeDownListingCommand comando) {
        Listing actual = publicaciones
                .buscar(comando.publicacion())
                .orElseThrow(() -> new ListingNotFoundException(comando.publicacion()));

        if (actual.laPublico(comando.moderador())) {
            throw new SelfModerationForbiddenException();
        }
        exigirQueHayaSidoVisible(actual);

        Listing retirada = publicaciones.guardar(actual.archivar(Instant.now(reloj)));

        bitacora.registrar(
                retirada.id(),
                comando.moderador(),
                ModerationAction.ARCHIVED,
                comando.motivo().name(),
                comando.nota());
        avisos.publicacionRetirada(retirada, comando.nota());

        // Lo que se retira por RN-024 no puede seguir servido. Es el caso mas claro:
        // una replica bajada del catalogo con sus fotos todavia accesibles.
        retirada.images().stream().map(ProductImage::objectKey).forEach(almacen::borrar);
        return retirada;
    }

    /**
     * Criterio 31: esto retira lo que <strong>ya era visible</strong>.
     *
     * <p>Sin la comprobacion, un moderador destruye de forma irreversible el borrador
     * privado de alguien —{@code DRAFT} y {@code REJECTED} tambien admiten archivar— y le
     * manda un correo de retirada por algo que nadie llego a ver. Lo que se rechaza antes
     * de publicar se rechaza con {@code RejectListingUseCase}, que si deja corregir.
     */
    private static void exigirQueHayaSidoVisible(Listing publicacion) {
        if (publicacion.status() != ListingStatus.PUBLISHED && publicacion.status() != ListingStatus.PAUSED) {
            throw new InvalidListingTransitionException(publicacion.status(), ListingStatus.ARCHIVED);
        }
    }
}
