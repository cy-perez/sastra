package co.sendik.catalog.rest;

import co.sendik.catalog.dto.FavoriteCommand;
import co.sendik.catalog.dto.FavoritePage;
import co.sendik.catalog.dto.FavoriteState;
import co.sendik.catalog.dto.ListCatalogQuery;
import co.sendik.catalog.dto.ListFavoritesQuery;
import co.sendik.catalog.model.BuyerId;
import co.sendik.catalog.model.ListingId;
import co.sendik.catalog.rest.dto.CatalogPageResponse;
import co.sendik.catalog.rest.dto.FavoriteStateResponse;
import co.sendik.catalog.rest.mapper.FavoriteCursors;
import co.sendik.catalog.rest.mapper.FavoritePages;
import co.sendik.catalog.usecase.AddFavoriteUseCase;
import co.sendik.catalog.usecase.ListFavoritesUseCase;
import co.sendik.catalog.usecase.ReadFavoriteStateUseCase;
import co.sendik.catalog.usecase.RemoveFavoriteUseCase;
import co.sendik.shared.port.out.PublicFileStore;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Los favoritos de quien pregunta. HU-011.
 *
 * <p><strong>Cuelga de {@code /users/me} y no de {@code /listings/{id}/favorite}.</strong>
 * El favorito es del usuario y no de la publicacion: bajo {@code /users/me} la regla de
 * seguridad ya es «autenticado» y no hay que inventar ninguna, y la ruta dice de quien es
 * el dato. Es la misma razon por la que {@code SellerVerificationController} vive alli.
 *
 * <p><strong>Detras de {@code FEATURE_CATALOG}.</strong> Con la bandera apagada el
 * controlador no se crea y las cuatro rutas responden 404: no rechazan, no estan. Y no hace
 * falta tocar {@code SecurityConfig} para conseguirlo, al reves que en las rutas de
 * moderacion: la regla de {@code /api/v1/users/**} es «autenticado», asi que la peticion
 * atraviesa la cadena, no encuentra manejador y sale el 404 que corresponde. Una regla por
 * rol habria respondido 403 en el filtro y con eso habria confirmado que la funcionalidad
 * esta ahi.
 *
 * <p><strong>El identificador de quien marca sale del token, nunca de la peticion.</strong>
 * Es la regla de backend/CLAUDE.md, y aqui es lo unico que impide llenar de favoritos la
 * cuenta de otra persona. La lista es privada (RN-070) y no hay ninguna ruta que acepte de
 * quien se quiere leer.
 *
 * <p>Ninguna de las cuatro decide nada: RN-072 —que nadie marque lo suyo— la comprueban el
 * dominio y el caso de uso, y aqui solo se traduce.
 */
@RestController
@Validated
@RequestMapping("/api/v1/users/me/favorites")
@ConditionalOnProperty(prefix = "sendik.features", name = "catalog", havingValue = "true")
public class FavoritesController {

    private final AddFavoriteUseCase casoDeMarcar;
    private final RemoveFavoriteUseCase casoDeQuitar;
    private final ReadFavoriteStateUseCase casoDeConsultar;
    private final ListFavoritesUseCase casoDeListar;
    private final PublicFileStore almacen;

    public FavoritesController(
            AddFavoriteUseCase casoDeMarcar,
            RemoveFavoriteUseCase casoDeQuitar,
            ReadFavoriteStateUseCase casoDeConsultar,
            ListFavoritesUseCase casoDeListar,
            PublicFileStore almacen) {
        this.casoDeMarcar = casoDeMarcar;
        this.casoDeQuitar = casoDeQuitar;
        this.casoDeConsultar = casoDeConsultar;
        this.casoDeListar = casoDeListar;
        this.almacen = almacen;
    }

    /**
     * Guarda la publicacion. Criterios 2 y 4.
     *
     * <p><strong>{@code PUT} y no {@code POST}, y 204 en vez de 201.</strong> Marcar es
     * idempotente: la misma peticion repetida deja el mismo favorito, que es exactamente lo
     * que {@code PUT} promete. Con {@code POST} y 201, el segundo intento —un reintento de
     * red, dos pestanas— tendria que elegir entre mentir con otro 201 o inventar un
     * conflicto que no existe.
     *
     * <p>Sin {@code Location}: el recurso siempre esta en esta misma direccion, asi que no
     * hay nada a lo que apuntar que el cliente no tenga ya.
     */
    @PutMapping("/{listingId}")
    public ResponseEntity<Void> marcar(@AuthenticationPrincipal Jwt token, @PathVariable String listingId) {
        casoDeMarcar.execute(new FavoriteCommand(quienDe(token), ListingId.de(listingId)));

        return ResponseEntity.noContent().build();
    }

    /**
     * Lo quita. Criterio 3.
     *
     * <p>Idempotente tambien: quitar lo que no esta responde 204 y no 404. Con un 404 ahi,
     * el doble pulsado acabaria en un mensaje de error sobre algo que salio exactamente
     * como se pidio.
     */
    @DeleteMapping("/{listingId}")
    public ResponseEntity<Void> quitar(@AuthenticationPrincipal Jwt token, @PathVariable String listingId) {
        casoDeQuitar.execute(new FavoriteCommand(quienDe(token), ListingId.de(listingId)));

        return ResponseEntity.noContent().build();
    }

    /**
     * El estado del control para una publicacion concreta. Criterios 1 y 5.
     *
     * <p><strong>Es la ruta que permite que la ficha publica siga siendo publica.</strong>
     * {@code GET /listings/{id}} responde hoy lo mismo para cualquiera y se renderiza en el
     * servidor; anadirle un campo «esto es favorito tuyo» la volveria distinta por persona
     * y arruinaria esa propiedad. El estado se pide aparte y desde el navegador, despues de
     * hidratar.
     *
     * <p>Lectura puntual y no un filtro sobre la lista: con trescientos favoritos, abrir una
     * ficha descargaria trescientas publicaciones para mirar una.
     */
    @GetMapping("/{listingId}")
    public FavoriteStateResponse estado(@AuthenticationPrincipal Jwt token, @PathVariable String listingId) {
        FavoriteState estado = casoDeConsultar.execute(new FavoriteCommand(quienDe(token), ListingId.de(listingId)));

        return new FavoriteStateResponse(estado.favorito(), estado.elegible());
    }

    /**
     * La lista propia, lo guardado mas recientemente primero. Criterios 11 a 15.
     *
     * <p>Misma forma de respuesta que el catalogo y mismo tope, porque es la misma rejilla
     * con las mismas tarjetas. El tope se declara aqui ademas de en
     * {@link ListFavoritesQuery}, y es redundante a proposito: el del caso de uso protege a
     * cualquiera que lo use, y el de aqui hace que el 400 salga antes de tocar la base y
     * con el nombre del parametro que el cliente escribio.
     *
     * <p>Un cursor que no descifre es un 400 y no un tramo arbitrario: ignorarlo en
     * silencio devolveria la primera pagina, y quien esta recorriendo su lista volveria al
     * principio sin enterarse.
     */
    @GetMapping
    public CatalogPageResponse lista(
            @AuthenticationPrincipal Jwt token,
            @RequestParam(name = "limit", defaultValue = "24") @Min(1) @Max(ListCatalogQuery.LIMITE_MAXIMO) int limit,
            @RequestParam(name = "cursor", required = false) @Nullable String cursor) {

        FavoritePage tramo =
                casoDeListar.execute(new ListFavoritesQuery(quienDe(token), FavoriteCursors.cursor(cursor), limit));

        return FavoritePages.de(tramo, almacen);
    }

    /**
     * El {@code sub} del token es el identificador de la cuenta.
     *
     * <p>Se usa siempre este y nunca un parametro de la peticion: es lo unico que el cliente
     * no puede elegir, y es lo que hace que RN-070 se sostenga sin ninguna comprobacion
     * adicional. No hay forma de nombrar la lista de otra persona porque no hay donde
     * escribirla.
     */
    private static BuyerId quienDe(Jwt token) {
        return BuyerId.de(token.getSubject());
    }
}
