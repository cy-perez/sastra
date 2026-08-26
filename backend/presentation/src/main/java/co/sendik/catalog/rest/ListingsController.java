package co.sendik.catalog.rest;

import co.sendik.catalog.dto.ChangeListingPriceCommand;
import co.sendik.catalog.dto.ChangeListingShippingCommand;
import co.sendik.catalog.dto.CreateListingCommand;
import co.sendik.catalog.dto.ReadListingQuery;
import co.sendik.catalog.dto.RemoveListingImageCommand;
import co.sendik.catalog.dto.SellerListingCommand;
import co.sendik.catalog.dto.UpdateListingContentCommand;
import co.sendik.catalog.dto.UploadListingImageCommand;
import co.sendik.catalog.model.ImageKind;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.model.ListingId;
import co.sendik.catalog.model.ProductImageId;
import co.sendik.catalog.model.SellerId;
import co.sendik.catalog.rest.dto.ChangePriceRequest;
import co.sendik.catalog.rest.dto.ListingResponse;
import co.sendik.catalog.rest.dto.ProductRequest;
import co.sendik.catalog.rest.dto.ShippingPayload;
import co.sendik.catalog.rest.mapper.ListingResponses;
import co.sendik.catalog.rest.mapper.ProductRequests;
import co.sendik.catalog.usecase.ArchiveListingUseCase;
import co.sendik.catalog.usecase.ChangeListingPriceUseCase;
import co.sendik.catalog.usecase.ChangeListingShippingUseCase;
import co.sendik.catalog.usecase.CreateListingUseCase;
import co.sendik.catalog.usecase.PauseListingUseCase;
import co.sendik.catalog.usecase.ReadListingUseCase;
import co.sendik.catalog.usecase.RemoveListingImageUseCase;
import co.sendik.catalog.usecase.ReopenListingUseCase;
import co.sendik.catalog.usecase.ResumeListingUseCase;
import co.sendik.catalog.usecase.SubmitListingForReviewUseCase;
import co.sendik.catalog.usecase.UpdateListingContentUseCase;
import co.sendik.catalog.usecase.UploadListingImageUseCase;
import co.sendik.catalog.usecase.WithdrawListingUseCase;
import co.sendik.shared.port.out.PublicFileStore;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * La publicacion de producto, del lado del vendedor. HU-007.
 *
 * <p><strong>Solo existe con la bandera encendida.</strong> Sin {@code FEATURE_PUBLISHING}
 * el controlador no se crea y las rutas responden 404: no es que rechacen, es que no
 * estan (criterio 3). Es lo mismo que hace la verificacion de vendedor y es mas honesto
 * que un 403, que le diria a cualquiera que la funcionalidad esta ahi.
 *
 * <p><strong>El identificador del vendedor sale del token, nunca de la peticion.</strong>
 * Es la regla de backend/CLAUDE.md, y aqui evita lo evidente: con un identificador en el
 * cuerpo, cualquiera editaria la publicacion de otra persona.
 *
 * <p>La lectura de una publicacion es la unica ruta de esta historia abierta a quien no
 * ha iniciado sesion, porque es la que el catalogo publico va a usar. Quien decide que se
 * ve no es la cadena de filtros sino el caso de uso, que responde vacio —y por tanto
 * 404— tanto si no existe como si no es para quien pregunta (criterio 33).
 *
 * <p>Aqui no se valida ningun contenido de imagen. Lo hace el caso de uso, que comprueba
 * tamano, tipo por los bytes de cabecera y dimensiones (ADR-0018): validar en el borde
 * dejaria la regla donde no se puede probar sin HTTP.
 */
@RestController
@RequestMapping("/api/v1/listings")
@ConditionalOnProperty(prefix = "sendik.features", name = "publishing", havingValue = "true")
public class ListingsController {

    /** El prefijo lo pone el convertidor de {@code SecurityConfig}; {@code hasRole} lo asume. */
    private static final String AUTORIDAD_DE_MODERADOR = "ROLE_MODERATOR";

    private final CreateListingUseCase casoDeCrear;
    private final ReadListingUseCase casoDeLeer;
    private final UpdateListingContentUseCase casoDeEditar;
    private final ChangeListingPriceUseCase casoDePrecio;
    private final ChangeListingShippingUseCase casoDeEnvio;
    private final UploadListingImageUseCase casoDeSubirImagen;
    private final RemoveListingImageUseCase casoDeBorrarImagen;
    private final SubmitListingForReviewUseCase casoDeEnviarARevision;
    private final WithdrawListingUseCase casoDeRetirarDeRevision;
    private final ReopenListingUseCase casoDeRetomar;
    private final PauseListingUseCase casoDePausar;
    private final ResumeListingUseCase casoDeReanudar;
    private final ArchiveListingUseCase casoDeArchivar;
    private final PublicFileStore almacen;

    public ListingsController(
            CreateListingUseCase casoDeCrear,
            ReadListingUseCase casoDeLeer,
            UpdateListingContentUseCase casoDeEditar,
            ChangeListingPriceUseCase casoDePrecio,
            ChangeListingShippingUseCase casoDeEnvio,
            UploadListingImageUseCase casoDeSubirImagen,
            RemoveListingImageUseCase casoDeBorrarImagen,
            SubmitListingForReviewUseCase casoDeEnviarARevision,
            WithdrawListingUseCase casoDeRetirarDeRevision,
            ReopenListingUseCase casoDeRetomar,
            PauseListingUseCase casoDePausar,
            ResumeListingUseCase casoDeReanudar,
            ArchiveListingUseCase casoDeArchivar,
            PublicFileStore almacen) {
        this.casoDeCrear = casoDeCrear;
        this.casoDeLeer = casoDeLeer;
        this.casoDeEditar = casoDeEditar;
        this.casoDePrecio = casoDePrecio;
        this.casoDeEnvio = casoDeEnvio;
        this.casoDeSubirImagen = casoDeSubirImagen;
        this.casoDeBorrarImagen = casoDeBorrarImagen;
        this.casoDeEnviarARevision = casoDeEnviarARevision;
        this.casoDeRetirarDeRevision = casoDeRetirarDeRevision;
        this.casoDeRetomar = casoDeRetomar;
        this.casoDePausar = casoDePausar;
        this.casoDeReanudar = casoDeReanudar;
        this.casoDeArchivar = casoDeArchivar;
        this.almacen = almacen;
    }

    /**
     * Crea el borrador. Criterio 4.
     *
     * <p>201 con {@code Location}, que es lo que contrato-api.md pide para una creacion.
     * Aqui si se puede, al reves que en el registro: nada que ocultar sobre si el recurso
     * existia, porque acaba de nacer.
     */
    @PostMapping
    public ResponseEntity<ListingResponse> crear(
            @AuthenticationPrincipal Jwt token, @Valid @RequestBody ProductRequest peticion) {

        Listing borrador =
                casoDeCrear.execute(new CreateListingCommand(vendedorDe(token), ProductRequests.aDatos(peticion)));

        return ResponseEntity.created(
                        URI.create("/api/v1/listings/" + borrador.id().value()))
                .body(ListingResponses.de(borrador, almacen));
    }

    /**
     * Una publicacion. Criterio 33.
     *
     * <p>Responde con una forma u otra segun quien pregunte: el dueno y el moderador ven
     * la version completa, cualquier otro ve la publica, que no lleva nada de moderacion.
     *
     * <p>El token puede no venir, y no es un caso raro: esta ruta la usara el catalogo
     * publico. Cuando no hay nadie identificado, solo se responde lo que esta publicado.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Object> una(
            @AuthenticationPrincipal @Nullable Jwt token,
            @Nullable Authentication autenticacion,
            @PathVariable String id) {

        boolean moderador = esModerador(autenticacion);
        SellerId quienMira = token == null ? null : vendedorDe(token);

        return casoDeLeer
                .execute(new ReadListingQuery(ListingId.de(id), quienMira, moderador))
                .<ResponseEntity<Object>>map(publicacion -> ResponseEntity.ok(
                        puedeVerLaCocina(publicacion, quienMira, moderador)
                                ? ListingResponses.de(publicacion, almacen)
                                : ListingResponses.publica(publicacion, almacen)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Guarda los datos del producto. Criterios 5 y 27.
     *
     * <p>Sobre una publicacion viva la devuelve a moderacion, porque cambia lo que
     * describe el producto (RN-062). El precio y el envio tienen sus propias rutas
     * justamente porque su consecuencia es la contraria.
     */
    @PatchMapping("/{id}")
    public ListingResponse editar(
            @AuthenticationPrincipal Jwt token, @PathVariable String id, @Valid @RequestBody ProductRequest peticion) {

        Listing editada = casoDeEditar.execute(
                new UpdateListingContentCommand(vendedorDe(token), ListingId.de(id), ProductRequests.aDatos(peticion)));

        return ListingResponses.de(editada, almacen);
    }

    /** Solo el precio. Sigue visible y no pasa por moderacion. Criterio 28, RN-030. */
    @PatchMapping("/{id}/price")
    public ListingResponse cambiarPrecio(
            @AuthenticationPrincipal Jwt token,
            @PathVariable String id,
            @Valid @RequestBody ChangePriceRequest peticion) {

        Listing conOtroPrecio = casoDePrecio.execute(new ChangeListingPriceCommand(
                vendedorDe(token), ListingId.de(id), ProductRequests.aDinero(peticion.price())));

        return ListingResponses.de(conOtroPrecio, almacen);
    }

    /** Solo el peso y la caja. Tampoco pasa por moderacion. Criterio 28. */
    @PatchMapping("/{id}/shipping")
    public ListingResponse cambiarEnvio(
            @AuthenticationPrincipal Jwt token, @PathVariable String id, @RequestBody ShippingPayload peticion) {

        Listing conOtroEnvio = casoDeEnvio.execute(new ChangeListingShippingCommand(
                vendedorDe(token), ListingId.de(id), ProductRequests.aDimensiones(peticion)));

        return ListingResponses.de(conOtroEnvio, almacen);
    }

    /**
     * Sube una toma o una imagen de referencia. Criterios 14, 15, 16 y 18.
     *
     * <p>Multipart y no base64 dentro de un JSON, por lo mismo que la cedula de HU-002:
     * en base64 ocupa un tercio mas y obliga a tener la imagen dos veces en memoria.
     *
     * <p>{@code desdeGaleria} lo declara el cliente y solo suma una marca de atencion:
     * el backend no puede distinguir una foto capturada de una elegida, asi que esto
     * nunca quita una validacion, solo agrega una sospecha (criterio 18).
     */
    @PostMapping("/{id}/images")
    public ResponseEntity<ListingResponse> subirImagen(
            @AuthenticationPrincipal Jwt token,
            @PathVariable String id,
            @RequestParam(name = "kind", defaultValue = "SELLER_SHOT") String clase,
            @RequestParam("position") int posicion,
            @RequestParam(name = "fromGallery", defaultValue = "false") boolean desdeGaleria,
            @RequestPart("archivo") MultipartFile archivo)
            throws IOException {

        Listing conImagen = casoDeSubirImagen.execute(new UploadListingImageCommand(
                vendedorDe(token), ListingId.de(id), claseDeImagen(clase), posicion, archivo.getBytes(), desdeGaleria));

        return ResponseEntity.status(201).body(ListingResponses.de(conImagen, almacen));
    }

    /** Borra una toma o una imagen de referencia, y con ella el archivo del almacen. */
    @DeleteMapping("/{id}/images/{imageId}")
    public ListingResponse borrarImagen(
            @AuthenticationPrincipal Jwt token, @PathVariable String id, @PathVariable String imageId) {

        Listing sinImagen = casoDeBorrarImagen.execute(
                new RemoveListingImageCommand(vendedorDe(token), ListingId.de(id), ProductImageId.de(imageId)));

        return ListingResponses.de(sinImagen, almacen);
    }

    /**
     * Envia a revision. Criterio 19.
     *
     * <p>POST sobre un subrecurso y no PUT del estado: enviar no es escribir un campo,
     * es un acto. El nombre es sustantivo porque contrato-api.md no admite verbos en la
     * ruta.
     */
    @PostMapping("/{id}/submission")
    public ListingResponse enviarARevision(@AuthenticationPrincipal Jwt token, @PathVariable String id) {
        return ListingResponses.de(
                casoDeEnviarARevision.execute(new SellerListingCommand(vendedorDe(token), ListingId.de(id))), almacen);
    }

    /** Retira la solicitud antes de que se decida. Criterio 20. */
    @DeleteMapping("/{id}/submission")
    public ListingResponse retirarDeRevision(@AuthenticationPrincipal Jwt token, @PathVariable String id) {
        return ListingResponses.de(
                casoDeRetirarDeRevision.execute(new SellerListingCommand(vendedorDe(token), ListingId.de(id))),
                almacen);
    }

    /**
     * Retoma una rechazada y la devuelve a borrador. Criterio 23.
     *
     * <p>Es un {@code DELETE} del rechazo por lo mismo que el de la solicitud: el
     * vendedor quita lo que hay, no crea nada. Sin esta ruta, a quien le rechazan por las
     * fotos no le queda forma de reenviar, porque el estado rechazado no pasa a revision
     * directamente y subir una toma no lo cambia.
     */
    @DeleteMapping("/{id}/rejection")
    public ListingResponse retomar(@AuthenticationPrincipal Jwt token, @PathVariable String id) {
        return ListingResponses.de(
                casoDeRetomar.execute(new SellerListingCommand(vendedorDe(token), ListingId.de(id))), almacen);
    }

    /** Pausa: deja de verse sin pasar por moderacion al volver. Criterio 29. */
    @PostMapping("/{id}/pause")
    public ListingResponse pausar(@AuthenticationPrincipal Jwt token, @PathVariable String id) {
        return ListingResponses.de(
                casoDePausar.execute(new SellerListingCommand(vendedorDe(token), ListingId.de(id))), almacen);
    }

    /** Reanuda. Criterio 29. */
    @DeleteMapping("/{id}/pause")
    public ListingResponse reanudar(@AuthenticationPrincipal Jwt token, @PathVariable String id) {
        return ListingResponses.de(
                casoDeReanudar.execute(new SellerListingCommand(vendedorDe(token), ListingId.de(id))), almacen);
    }

    /**
     * Archiva. Criterio 30: no vuelve de ahi.
     *
     * <p>Es del vendedor y no lleva motivo. Cuando quien retira es el moderador, la ruta
     * es otra y exige motivo, porque son dos actos distintos con dos consecuencias
     * distintas (criterio 31).
     */
    @PostMapping("/{id}/archival")
    public ListingResponse archivar(@AuthenticationPrincipal Jwt token, @PathVariable String id) {
        return ListingResponses.de(
                casoDeArchivar.execute(new SellerListingCommand(vendedorDe(token), ListingId.de(id))), almacen);
    }

    /**
     * La forma completa es para el dueno y el moderador; el resto ve la publica.
     *
     * <p>No es una regla de negocio sino la eleccion de la representacion: quien puede
     * ver la publicacion ya lo decidio el caso de uso.
     */
    private static boolean puedeVerLaCocina(Listing publicacion, @Nullable SellerId quienMira, boolean moderador) {
        return moderador || publicacion.sellerId().equals(quienMira);
    }

    /** El {@code sub} del token. Es lo unico que el cliente no puede elegir. */
    private static SellerId vendedorDe(Jwt token) {
        return SellerId.de(token.getSubject());
    }

    /**
     * El rol sale de las autoridades que ya calculo la cadena de filtros.
     *
     * <p><strong>No se lee el claim a mano.</strong> Lo intente asi y era la unica lectura
     * de autorizacion del repositorio que no pasaba por Spring Security: el dia que el
     * convertidor de {@code SecurityConfig} cambie de claim o de prefijo, esto y
     * {@code hasRole} dejarian de coincidir en silencio, y lo que se rompe es cuanto se
     * cuenta de la publicacion de otra persona.
     *
     * <p>Se decide aqui y no con {@code @PreAuthorize} porque no autoriza el acceso —la
     * ruta es publica a proposito— sino que elige la representacion.
     */
    private static boolean esModerador(@Nullable Authentication autenticacion) {
        if (autenticacion == null) {
            return false;
        }
        return autenticacion.getAuthorities().contains(new SimpleGrantedAuthority(AUTORIDAD_DE_MODERADOR));
    }

    /**
     * Se convierte a mano por lo mismo que en los controladores de identidad: con la
     * enumeracion en la firma, un valor desconocido sale como 500 por un fallo de
     * conversion que nadie mapea.
     */
    private static ImageKind claseDeImagen(String valor) {
        try {
            return ImageKind.valueOf(valor.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("La clase de imagen no es SELLER_SHOT ni REFERENCE", e);
        }
    }
}
