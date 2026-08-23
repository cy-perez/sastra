package co.sastra.identity.rest;

import co.sastra.identity.dto.ApproveVerificationCommand;
import co.sastra.identity.dto.RejectVerificationCommand;
import co.sastra.identity.dto.RevokeVerificationCommand;
import co.sastra.identity.dto.VerificationImageContent;
import co.sastra.identity.dto.ViewVerificationImageCommand;
import co.sastra.identity.model.RejectionReason;
import co.sastra.identity.model.SellerVerification;
import co.sastra.identity.model.SellerVerificationId;
import co.sastra.identity.model.UserId;
import co.sastra.identity.model.VerificationImage;
import co.sastra.identity.rest.dto.PendingVerificationResponse;
import co.sastra.identity.rest.dto.RejectVerificationRequest;
import co.sastra.identity.rest.dto.SellerVerificationResponse;
import co.sastra.identity.rest.mapper.PendingVerificationResponses;
import co.sastra.identity.rest.mapper.SellerVerificationResponses;
import co.sastra.identity.usecase.ApproveVerificationUseCase;
import co.sastra.identity.usecase.ListPendingVerificationsUseCase;
import co.sastra.identity.usecase.RejectVerificationUseCase;
import co.sastra.identity.usecase.RevokeVerificationUseCase;
import co.sastra.identity.usecase.ViewVerificationImageUseCase;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * La revision de verificaciones, del lado del moderador. HU-002, criterios 7 y 8.
 *
 * <p><strong>Fuera de {@code /api/v1/users/**} a proposito, y esto es lo que importa de
 * este archivo.</strong> Esa ruta tiene una regla de seguridad que solo exige token: si
 * estos endpoints vivieran ahi, cualquier persona autenticada podria aprobar su propia
 * verificacion. Aqui la ruta es {@code /api/v1/verifications} y su regla exige
 * {@code hasRole("MODERATOR")}, declarada en {@code SecurityConfig}.
 *
 * <p>Y encima de eso, {@link PreAuthorize} en cada metodo. Es deliberadamente redundante:
 * la regla de la cadena protege por ruta y esta por metodo, asi que mover un endpoint de
 * sitio no se lleva su autorizacion por delante. Es la clase de cosa que no se nota hasta
 * que pasa.
 *
 * <p>Lo que <strong>no</strong> comprueba nadie todavia: que el moderador no sea la misma
 * persona que la solicitud. Ninguna regla de negocio lo prohibe por escrito y no se
 * inventa aqui; esta anotado en HU-002 como decision pendiente.
 *
 * <p>Solo existe con {@code FEATURE_SELLER_VERIFICATION} encendida, igual que el lado de
 * quien se verifica.
 */
@RestController
@RequestMapping("/api/v1/verifications")
@ConditionalOnProperty(prefix = "sastra.features", name = "seller-verification", havingValue = "true")
public class VerificationReviewController {

    private final ListPendingVerificationsUseCase casoDeListar;
    private final ViewVerificationImageUseCase casoDeVerImagen;
    private final ApproveVerificationUseCase casoDeAprobar;
    private final RejectVerificationUseCase casoDeRechazar;
    private final RevokeVerificationUseCase casoDeRevocar;

    public VerificationReviewController(
            ListPendingVerificationsUseCase casoDeListar,
            ViewVerificationImageUseCase casoDeVerImagen,
            ApproveVerificationUseCase casoDeAprobar,
            RejectVerificationUseCase casoDeRechazar,
            RevokeVerificationUseCase casoDeRevocar) {
        this.casoDeListar = casoDeListar;
        this.casoDeVerImagen = casoDeVerImagen;
        this.casoDeAprobar = casoDeAprobar;
        this.casoDeRechazar = casoDeRechazar;
        this.casoDeRevocar = casoDeRevocar;
    }

    /** La bandeja: lo que espera revision, lo mas viejo primero. */
    @GetMapping
    @PreAuthorize("hasRole('MODERATOR')")
    public List<PendingVerificationResponse> pendientes(
            @AuthenticationPrincipal Jwt token, @RequestParam(name = "limite", defaultValue = "20") int limite) {

        UserId quienMira = moderadorDe(token);

        return casoDeListar.execute(limite).stream()
                .map(verificacion -> PendingVerificationResponses.de(verificacion, quienMira))
                .toList();
    }

    /**
     * Una de las tres imagenes, con la lectura anotada en la bitacora.
     *
     * <p>Devuelve los bytes y no una direccion, tampoco firmada: es lo que permite saber
     * quien miro (ADR-0018, RN-046).
     *
     * <p>{@code no-store} porque esto no se cachea en ninguna parte. Una cedula en la
     * cache de un navegador compartido, o en un intermediario, es exactamente el problema
     * que el almacen reservado existe para evitar.
     */
    @GetMapping("/{id}/images/{imagen}")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<byte[]> imagen(
            @AuthenticationPrincipal Jwt token,
            @PathVariable String id,
            @PathVariable String imagen,
            @RequestParam(name = "motivo", required = false) String motivo) {

        VerificationImageContent contenido = casoDeVerImagen.execute(new ViewVerificationImageCommand(
                moderadorDe(token), SellerVerificationId.de(id), imagenDe(imagen), motivo));

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contenido.mediaType()))
                .header("Cache-Control", "no-store")
                .body(contenido.contenido());
    }

    /**
     * Aprobar. Criterio 8.
     *
     * <p>POST sobre un subrecurso y no PUT del estado: aprobar es un acto que otorga un
     * rol y queda en bitacora, no la escritura de un campo.
     */
    @PostMapping("/{id}/approval")
    @PreAuthorize("hasRole('MODERATOR')")
    public SellerVerificationResponse aprobar(@AuthenticationPrincipal Jwt token, @PathVariable String id) {
        SellerVerification aprobada =
                casoDeAprobar.execute(new ApproveVerificationCommand(moderadorDe(token), SellerVerificationId.de(id)));

        return SellerVerificationResponses.de(aprobada);
    }

    /** Rechazar con motivo de la lista cerrada y nota opcional. Criterio 7. */
    @PostMapping("/{id}/rejection")
    @PreAuthorize("hasRole('MODERATOR')")
    public SellerVerificationResponse rechazar(
            @AuthenticationPrincipal Jwt token,
            @PathVariable String id,
            @Valid @RequestBody RejectVerificationRequest peticion) {

        SellerVerification rechazada = casoDeRechazar.execute(new RejectVerificationCommand(
                moderadorDe(token), SellerVerificationId.de(id), motivoDe(peticion.reason()), peticion.note()));

        return SellerVerificationResponses.de(rechazada);
    }

    /** Revocar el sello de quien ya lo tenia. RN-013. */
    @PostMapping("/{id}/revocation")
    @PreAuthorize("hasRole('MODERATOR')")
    public SellerVerificationResponse revocar(
            @AuthenticationPrincipal Jwt token,
            @PathVariable String id,
            @Valid @RequestBody RejectVerificationRequest peticion) {

        SellerVerification revocada = casoDeRevocar.execute(new RevokeVerificationCommand(
                moderadorDe(token), SellerVerificationId.de(id), motivoDe(peticion.reason()), peticion.note()));

        return SellerVerificationResponses.de(revocada);
    }

    private static UserId moderadorDe(Jwt token) {
        return UserId.de(token.getSubject());
    }

    /**
     * Se convierte a mano por lo mismo que en el otro controlador: con el enum en la
     * firma, un valor desconocido sale como 500 por un fallo de conversion que nadie
     * mapea.
     */
    private static RejectionReason motivoDe(String valor) {
        try {
            return RejectionReason.valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El motivo no es uno de los de la lista cerrada", e);
        }
    }

    private static VerificationImage imagenDe(String valor) {
        try {
            return VerificationImage.valueOf(
                    valor.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("La imagen no es document-front, document-back ni selfie", e);
        }
    }
}
