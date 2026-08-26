package co.sendik.catalog.rest;

import co.sendik.catalog.dto.ApproveListingCommand;
import co.sendik.catalog.dto.RejectListingCommand;
import co.sendik.catalog.dto.TakeDownListingCommand;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.model.ListingId;
import co.sendik.catalog.model.ListingRejectionReason;
import co.sendik.catalog.model.ModeratorId;
import co.sendik.catalog.rest.dto.ListingResponse;
import co.sendik.catalog.rest.dto.RejectListingRequest;
import co.sendik.catalog.rest.mapper.ListingResponses;
import co.sendik.catalog.usecase.ApproveListingUseCase;
import co.sendik.catalog.usecase.RejectListingUseCase;
import co.sendik.catalog.usecase.TakeDownListingUseCase;
import co.sendik.shared.port.out.PublicFileStore;
import jakarta.validation.Valid;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La decision sobre una publicacion, del lado del moderador. HU-007, criterios 21, 22 y 31.
 *
 * <p><strong>Comparte ruta base con el vendedor y por eso la autorizacion va por metodo y
 * ruta, no por prefijo.</strong> En HU-002 los endpoints del moderador viven en
 * {@code /api/v1/verifications} y una sola regla de prefijo los protege; aqui la historia
 * los pone bajo {@code /api/v1/listings/{id}}, que es tambien donde escribe el vendedor.
 * La cadena de filtros los separa por metodo y patron, y encima va {@link PreAuthorize} en
 * cada uno, redundante a proposito: mover un endpoint de sitio no se lleva su autorizacion
 * por delante.
 *
 * <p>Que el moderador no sea el vendedor de esa misma publicacion lo impone RN-063, y no
 * aqui: vive en los casos de uso, que son los unicos que conocen al dueno. Sale como 403
 * con {@code CATALOG_SELF_MODERATION_FORBIDDEN} (criterio 24).
 *
 * <p>La bandeja con la que se usan estos endpoints es otra historia, con el mismo corte
 * que hubo entre HU-002 y HU-006. Aqui quedan las decisiones, probadas.
 */
@RestController
@RequestMapping("/api/v1/listings")
@ConditionalOnProperty(prefix = "sendik.features", name = "publishing", havingValue = "true")
public class ListingReviewController {

    private final ApproveListingUseCase casoDeAprobar;
    private final RejectListingUseCase casoDeRechazar;
    private final TakeDownListingUseCase casoDeRetirar;
    private final PublicFileStore almacen;

    public ListingReviewController(
            ApproveListingUseCase casoDeAprobar,
            RejectListingUseCase casoDeRechazar,
            TakeDownListingUseCase casoDeRetirar,
            PublicFileStore almacen) {
        this.casoDeAprobar = casoDeAprobar;
        this.casoDeRechazar = casoDeRechazar;
        this.casoDeRetirar = casoDeRetirar;
        this.almacen = almacen;
    }

    /**
     * Aprueba y publica. Criterio 21.
     *
     * <p>POST sobre un subrecurso y no PUT del estado: aprobar es un acto que deja rastro
     * en la bitacora de moderacion, no la escritura de un campo.
     */
    @PostMapping("/{id}/approval")
    @PreAuthorize("hasRole('MODERATOR')")
    public ListingResponse aprobar(@AuthenticationPrincipal Jwt token, @PathVariable String id) {
        Listing aprobada = casoDeAprobar.execute(new ApproveListingCommand(moderadorDe(token), ListingId.de(id)));

        return ListingResponses.de(aprobada, almacen);
    }

    /** Rechaza con motivo de la lista cerrada y nota opcional. Criterio 22. */
    @PostMapping("/{id}/rejection")
    @PreAuthorize("hasRole('MODERATOR')")
    public ListingResponse rechazar(
            @AuthenticationPrincipal Jwt token,
            @PathVariable String id,
            @Valid @RequestBody RejectListingRequest peticion) {

        Listing rechazada = casoDeRechazar.execute(new RejectListingCommand(
                moderadorDe(token), ListingId.de(id), motivoDe(peticion.reason()), peticion.note()));

        return ListingResponses.de(rechazada, almacen);
    }

    /**
     * Baja algo que ya era visible. Criterio 31 y RN-024.
     *
     * <p>Ruta propia y no la de archivar del vendedor, aunque el estado final sea el
     * mismo. Son dos actos distintos: el vendedor archiva lo suyo y no da explicaciones;
     * el moderador retira lo de otra persona y el motivo es obligatorio porque va en el
     * correo que la avisa. Con una sola ruta, la autorizacion no podria exigir rol y el
     * cuerpo tendria un campo obligatorio para uno y prohibido para el otro.
     */
    @PostMapping("/{id}/removal")
    @PreAuthorize("hasRole('MODERATOR')")
    public ListingResponse retirar(
            @AuthenticationPrincipal Jwt token,
            @PathVariable String id,
            @Valid @RequestBody RejectListingRequest peticion) {

        Listing retirada = casoDeRetirar.execute(new TakeDownListingCommand(
                moderadorDe(token), ListingId.de(id), motivoDe(peticion.reason()), peticion.note()));

        return ListingResponses.de(retirada, almacen);
    }

    private static ModeratorId moderadorDe(Jwt token) {
        return ModeratorId.de(token.getSubject());
    }

    /**
     * Se convierte a mano por lo mismo que en los controladores de identidad: con la
     * enumeracion en la firma, un valor desconocido sale como 500 por un fallo de
     * conversion que nadie mapea.
     */
    private static ListingRejectionReason motivoDe(String valor) {
        try {
            return ListingRejectionReason.valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El motivo no es uno de los de la lista cerrada", e);
        }
    }
}
