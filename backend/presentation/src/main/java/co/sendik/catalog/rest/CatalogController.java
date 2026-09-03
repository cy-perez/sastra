package co.sendik.catalog.rest;

import co.sendik.catalog.dto.CatalogPage;
import co.sendik.catalog.dto.ListCatalogQuery;
import co.sendik.catalog.model.CategoryId;
import co.sendik.catalog.rest.dto.CatalogPageResponse;
import co.sendik.catalog.rest.mapper.CatalogCursors;
import co.sendik.catalog.rest.mapper.CatalogPages;
import co.sendik.catalog.usecase.ListCatalogUseCase;
import co.sendik.shared.port.out.PublicFileStore;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * El catalogo publico. HU-009.
 *
 * <p><strong>Publico y sin token, que es toda la historia.</strong> Es la primera ruta del
 * proyecto que sirve a alguien que no tiene cuenta, y por eso el identificador de quien
 * pregunta no entra: RN-068 dice que se ve lo mismo con sesion y sin ella, tambien para el
 * dueno de la publicacion, que ve lo suyo en su panel.
 *
 * <p><strong>Cuelga de {@code /api/v1/listings} porque el contrato ya se lo reservo.</strong>
 * contrato-api.md ilustra la paginacion por cursor con esta ruta exacta y explica por que
 * la cola del moderador tuvo que irse a {@code /moderation}: son dos listas del mismo
 * recurso con paginacion distinta, y juntarlas dejaria la autorizacion colgando de un
 * parametro de consulta. Colgarlo de {@code /catalog/listings} habria dejado esa decision
 * sin efecto y el contrato con dos rutas para lo mismo.
 *
 * <p>La lectura de una publicacion por identificador **no esta aqui**: la sirve
 * {@code ListingsController}, que ya responde la forma publica a quien no es dueno ni
 * moderador desde HU-007. Duplicarla seria tener dos sitios donde decidir que se ensena.
 *
 * <p><strong>Detras de {@code FEATURE_CATALOG}.</strong> Con la bandera apagada el
 * controlador no se crea y la ruta responde 404: no rechaza, no esta. Es lo mismo que
 * hacen la verificacion de vendedor y la publicacion, y es lo que impide que un 403
 * confirme que el catalogo esta ahi esperando.
 */
@RestController
@Validated
@RequestMapping("/api/v1/listings")
@ConditionalOnProperty(prefix = "sendik.features", name = "catalog", havingValue = "true")
public class CatalogController {

    private final ListCatalogUseCase casoDeListar;
    private final PublicFileStore almacen;

    public CatalogController(ListCatalogUseCase casoDeListar, PublicFileStore almacen) {
        this.casoDeListar = casoDeListar;
        this.almacen = almacen;
    }

    /**
     * Un tramo del catalogo, lo mas reciente primero.
     *
     * <p>{@code category} admite tanto una hoja como una familia: el caso de uso resuelve
     * las categorias publicables que cuelgan de ella, porque no se publica en una familia
     * sino en una categoria suya. Una categoria retirada del arbol sale como 404 y no como
     * listado vacio, que se leeria como «existe y no tiene nada».
     *
     * <p>El tope del limit lo pone {@link ListCatalogQuery} y ademas se declara aqui. Es
     * redundante a proposito, igual que en la bandeja del moderador: el del caso de uso
     * protege a cualquiera que lo use, y el de aqui hace que el 400 salga antes de tocar
     * la base y con el nombre del parametro que el cliente escribio.
     */
    @GetMapping
    public CatalogPageResponse catalogo(
            @RequestParam(name = "limit", defaultValue = "24") @Min(1) @Max(ListCatalogQuery.LIMITE_MAXIMO) int limit,
            @RequestParam(name = "cursor", required = false) @Nullable String cursor,
            @RequestParam(name = "category", required = false) @Nullable String categoria) {

        CatalogPage tramo = casoDeListar.execute(new ListCatalogQuery(
                categoria == null || categoria.isBlank() ? null : CategoryId.de(categoria),
                CatalogCursors.cursor(cursor),
                limit));

        return CatalogPages.de(tramo, almacen);
    }
}
