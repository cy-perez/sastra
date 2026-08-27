package co.sendik.catalog.rest;

import co.sendik.catalog.dto.ListSellerListingsQuery;
import co.sendik.catalog.model.SellerId;
import co.sendik.catalog.rest.dto.ListingResponse;
import co.sendik.catalog.rest.dto.SellerListingsPage;
import co.sendik.catalog.rest.mapper.ListingResponses;
import co.sendik.catalog.usecase.ListSellerListingsUseCase;
import co.sendik.shared.port.out.PublicFileStore;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las publicaciones propias del vendedor. HU-007, ultimo endpoint de la tabla.
 *
 * <p>Vive bajo {@code /api/v1/users/me} y no bajo {@code /api/v1/listings} porque el
 * recurso no es el catalogo sino lo que tiene esta cuenta: es la pantalla desde la que el
 * vendedor llega a sus borradores. De paso hereda la regla de seguridad de
 * {@code users/**}, que exige token.
 *
 * <p>Devuelve la forma completa y no la publica: todo lo que sale es de quien pregunta,
 * y necesita ver por que una publicacion suya esta marcada o con que motivo se la
 * rechazaron.
 */
@RestController
@RequestMapping("/api/v1/users/me/listings")
@ConditionalOnProperty(prefix = "sendik.features", name = "publishing", havingValue = "true")
public class SellerListingsController {

    private final ListSellerListingsUseCase casoDeListar;
    private final PublicFileStore almacen;

    public SellerListingsController(ListSellerListingsUseCase casoDeListar, PublicFileStore almacen) {
        this.casoDeListar = casoDeListar;
        this.almacen = almacen;
    }

    /**
     * Lo suyo, lo mas reciente primero.
     *
     * <p>El tamano lo acota {@link ListSellerListingsQuery} y no este metodo: un tope
     * escrito aqui protegeria esta ruta y ninguna otra que use el mismo caso de uso.
     */
    @GetMapping
    public SellerListingsPage mias(
            @AuthenticationPrincipal Jwt token,
            @RequestParam(name = "page", defaultValue = "0") int pagina,
            @RequestParam(name = "size", defaultValue = "20") int tamano) {

        List<ListingResponse> suyas =
                casoDeListar
                        .execute(new ListSellerListingsQuery(SellerId.de(token.getSubject()), pagina, tamano))
                        .stream()
                        .map(publicacion -> ListingResponses.de(publicacion, almacen))
                        .toList();

        return new SellerListingsPage(suyas, pagina, tamano);
    }
}
