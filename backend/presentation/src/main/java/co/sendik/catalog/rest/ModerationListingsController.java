package co.sendik.catalog.rest;

import co.sendik.catalog.dto.ListPendingListingsQuery;
import co.sendik.catalog.dto.PendingListingsResult;
import co.sendik.catalog.model.ModeratorId;
import co.sendik.catalog.rest.dto.PendingListingResponse;
import co.sendik.catalog.rest.dto.PendingListingsPage;
import co.sendik.catalog.rest.mapper.PendingListingResponses;
import co.sendik.catalog.usecase.ListPendingListingsUseCase;
import co.sendik.shared.port.out.PublicFileStore;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * La bandeja del moderador de publicaciones. HU-008, criterio 1.
 *
 * <p><strong>Vive en {@code /api/v1/moderation} y no bajo {@code /api/v1/listings}</strong>,
 * por dos razones que se refuerzan. La primera es del contrato: {@code GET
 * /api/v1/listings} esta reservado al catalogo publico, paginado por cursor porque entra
 * contenido constantemente; esta es una lista administrativa acotada, que el mismo
 * contrato deja paginar por page y size. La segunda es de seguridad: colgarla de
 * {@code /listings/&#123;algo&#125;} la pondria a competir con la regla que hace publica la
 * lectura de una publicacion, y esa regla ya avisa en {@code SecurityConfig} de que un
 * segmento literal —{@code /queue}, {@code /pending}— casa igual que un identificador.
 *
 * <p>Exige rol por partida doble: la regla de {@code SecurityConfig} sobre
 * {@code /api/v1/moderation/**} y el {@link PreAuthorize} de aqui. Es redundante a
 * proposito, como en la revision de verificaciones: mover un endpoint de sitio no se lleva
 * su autorizacion por delante.
 *
 * <p>No hay endpoint de detalle. Lo da {@code GET /api/v1/listings/&#123;id&#125;}, que ya
 * responde la forma completa a un moderador, y las tres decisiones —aprobar, rechazar,
 * bajar— siguen donde las dejo HU-007.
 */
@RestController
@Validated
@RequestMapping("/api/v1/moderation/listings")
@ConditionalOnProperty(prefix = "sendik.features", name = "publishing", havingValue = "true")
public class ModerationListingsController {

    private final ListPendingListingsUseCase casoDeListar;
    private final PublicFileStore almacen;

    public ModerationListingsController(ListPendingListingsUseCase casoDeListar, PublicFileStore almacen) {
        this.casoDeListar = casoDeListar;
        this.almacen = almacen;
    }

    /**
     * Lo que espera revision, lo que lleva mas tiempo primero.
     *
     * <p>La cola es una sola y no se filtra por quien pregunta: RN-063 le prohibe al
     * moderador decidir sobre lo suyo, no verlo. Esconderselo le impediria saber que su
     * publicacion esta en la fila, y de eso se encarga {@code own} en cada fila.
     *
     * <p>El tope lo acota {@link ListPendingListingsQuery} y no este metodo: escrito aqui
     * protegeria esta ruta y ninguna otra que use el mismo caso de uso.
     */
    @GetMapping
    @PreAuthorize("hasRole('MODERATOR')")
    public PendingListingsPage pendientes(
            @AuthenticationPrincipal Jwt token,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(50) int size) {

        ModeratorId quienModera = ModeratorId.de(token.getSubject());

        PendingListingsResult resultado = casoDeListar.execute(new ListPendingListingsQuery(page, size));

        List<PendingListingResponse> cola = resultado.items().stream()
                .map(publicacion -> PendingListingResponses.de(publicacion, quienModera, almacen))
                .toList();

        return new PendingListingsPage(cola, page, size, resultado.hayMas());
    }
}
