package co.sendik.catalog.rest;

import co.sendik.catalog.dto.ListCatalogQuery;
import co.sendik.catalog.dto.ListSellerCatalogQuery;
import co.sendik.catalog.dto.SellerProfileView;
import co.sendik.catalog.model.SellerId;
import co.sendik.catalog.rest.dto.CatalogPageResponse;
import co.sendik.catalog.rest.dto.SellerProfileResponse;
import co.sendik.catalog.rest.mapper.CatalogCursors;
import co.sendik.catalog.rest.mapper.CatalogPages;
import co.sendik.catalog.usecase.ListSellerCatalogUseCase;
import co.sendik.catalog.usecase.ReadSellerProfileUseCase;
import co.sendik.shared.port.out.PublicFileStore;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * El vendedor visto desde fuera. HU-009, criterios 18 a 21.
 *
 * <p>Dos rutas: quien es y que vende. Estan separadas porque se piden en momentos
 * distintos —el encabezado se pinta una vez y el escaparate se pagina— y juntarlas
 * obligaria a devolver el perfil entero en cada tramo.
 *
 * <p><strong>Cuelga de {@code /sellers} y no de {@code /users}.</strong> {@code /users/**}
 * exige token en la cadena de seguridad y esta abre datos de una persona a cualquiera; que
 * sean rutas distintas hace que la diferencia se vea en el prefijo y no en un matiz de una
 * regla. Ademas «vendedor» es el rol que importa aqui: de una persona se publica lo que
 * vende, no quien es.
 *
 * <p><strong>Nada de lo que sale de aqui es personal mas alla del nombre y la foto.</strong>
 * No lo garantiza este controlador acordandose de filtrar: lo garantiza
 * {@code PublicProfileView}, que es lo unico que {@code identity} deja cruzar hacia una
 * pantalla publica, y {@code SellerProfileResponse}, que no tiene campo donde meter nada
 * mas.
 *
 * <p>Detras de {@code FEATURE_CATALOG}, como el resto de la historia.
 */
@RestController
@Validated
@RequestMapping("/api/v1/sellers")
@ConditionalOnProperty(prefix = "sendik.features", name = "catalog", havingValue = "true")
public class SellersController {

    private final ReadSellerProfileUseCase casoDeLeerPerfil;
    private final ListSellerCatalogUseCase casoDeListar;
    private final PublicFileStore almacen;

    public SellersController(
            ReadSellerProfileUseCase casoDeLeerPerfil, ListSellerCatalogUseCase casoDeListar, PublicFileStore almacen) {
        this.casoDeLeerPerfil = casoDeLeerPerfil;
        this.casoDeListar = casoDeListar;
        this.almacen = almacen;
    }

    /**
     * Quien es.
     *
     * <p>404 si no existe, si el identificador no es de nadie o si la cuenta se cerro. Las
     * tres responden igual: distinguirlas confirmaria que una persona estuvo aqui, que es
     * justo lo que cerrar una cuenta pide que no se haga.
     */
    @GetMapping("/{id}")
    public ResponseEntity<SellerProfileResponse> perfil(@PathVariable String id) {
        return casoDeLeerPerfil
                .execute(new SellerId(UUID.fromString(id)))
                .map(this::de)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Que vende, ahora mismo.
     *
     * <p>Solo lo publicado (RN-068). No es el listado del panel del vendedor, que trae los
     * siete estados: son dos listas del mismo vendedor y solo una es publica.
     *
     * <p>Un vendedor que no existe responde aqui un tramo vacio y no 404. La pantalla ya
     * pregunto por el perfil, que si distingue: repetir esa comprobacion aqui costaria un
     * viaje mas para decir lo mismo.
     */
    @GetMapping("/{id}/listings")
    public CatalogPageResponse publicaciones(
            @PathVariable String id,
            @RequestParam(name = "limit", defaultValue = "24") @Min(1) @Max(ListCatalogQuery.LIMITE_MAXIMO) int limite,
            @RequestParam(name = "cursor", required = false) @Nullable String cursor) {

        return CatalogPages.de(
                casoDeListar.execute(new ListSellerCatalogQuery(
                        new SellerId(UUID.fromString(id)), CatalogCursors.cursor(cursor), limite)),
                almacen);
    }

    /**
     * La direccion de la foto la compone el almacen y no este controlador.
     *
     * <p>La clave guardada es opaca y quien sabe convertirla en una direccion es el
     * almacen, que ademas cambia entre local y nube (ADR-0018). Es lo mismo que ya hace
     * {@code ListingResponses} con las tomas.
     */
    private SellerProfileResponse de(SellerProfileView vendedor) {
        return new SellerProfileResponse(
                vendedor.id().value().toString(),
                vendedor.nombre(),
                vendedor.avatar() == null
                        ? null
                        : almacen.direccionDe(vendedor.avatar()).toString(),
                vendedor.verificado());
    }
}
