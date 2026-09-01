package co.sendik.identity.rest;

import co.sendik.identity.dto.ApproveVerificationCommand;
import co.sendik.identity.dto.RejectVerificationCommand;
import co.sendik.identity.dto.RevokeVerificationCommand;
import co.sendik.identity.dto.VerificationImageContent;
import co.sendik.identity.dto.ViewVerificationImageCommand;
import co.sendik.identity.model.RejectionReason;
import co.sendik.identity.model.RevocationReason;
import co.sendik.identity.model.SellerVerification;
import co.sendik.identity.model.SellerVerificationId;
import co.sendik.identity.model.UserId;
import co.sendik.identity.model.VerificationImage;
import co.sendik.identity.rest.dto.PendingVerificationResponse;
import co.sendik.identity.rest.dto.RejectVerificationRequest;
import co.sendik.identity.rest.dto.RevokeVerificationRequest;
import co.sendik.identity.rest.dto.SellerVerificationResponse;
import co.sendik.identity.rest.dto.SellerVerificationSummaryResponse;
import co.sendik.identity.rest.mapper.PendingVerificationResponses;
import co.sendik.identity.rest.mapper.SellerVerificationResponses;
import co.sendik.identity.usecase.ApproveVerificationUseCase;
import co.sendik.identity.usecase.ListPendingVerificationsUseCase;
import co.sendik.identity.usecase.ReadSellerVerificationUseCase;
import co.sendik.identity.usecase.RejectVerificationUseCase;
import co.sendik.identity.usecase.RevokeVerificationUseCase;
import co.sendik.identity.usecase.ViewVerificationImageUseCase;
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
 * <p>Que el moderador no sea la misma persona que la solicitud lo impone RN-060, y no
 * aqui: vive en los casos de uso de aprobar y rechazar, que son los unicos que conocen al
 * dueno. Sale como 403 con {@code SELLER_SELF_REVIEW_FORBIDDEN}. La bandeja ademas lo
 * dice antes, con el campo {@code own} de cada fila.
 *
 * <p>Solo existe con {@code FEATURE_SELLER_VERIFICATION} encendida, igual que el lado de
 * quien se verifica.
 */
@RestController
@RequestMapping("/api/v1/verifications")
@ConditionalOnProperty(prefix = "sendik.features", name = "seller-verification", havingValue = "true")
public class VerificationReviewController {

    private final ListPendingVerificationsUseCase casoDeListar;
    private final ViewVerificationImageUseCase casoDeVerImagen;
    private final ApproveVerificationUseCase casoDeAprobar;
    private final RejectVerificationUseCase casoDeRechazar;
    private final RevokeVerificationUseCase casoDeRevocar;
    private final ReadSellerVerificationUseCase casoDeLeer;

    public VerificationReviewController(
            ListPendingVerificationsUseCase casoDeListar,
            ViewVerificationImageUseCase casoDeVerImagen,
            ApproveVerificationUseCase casoDeAprobar,
            RejectVerificationUseCase casoDeRechazar,
            RevokeVerificationUseCase casoDeRevocar,
            ReadSellerVerificationUseCase casoDeLeer) {
        this.casoDeListar = casoDeListar;
        this.casoDeVerImagen = casoDeVerImagen;
        this.casoDeAprobar = casoDeAprobar;
        this.casoDeRechazar = casoDeRechazar;
        this.casoDeRevocar = casoDeRevocar;
        this.casoDeLeer = casoDeLeer;
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
     * La verificacion de un vendedor, por su identificador de cuenta. HU-010.
     *
     * <p>Existe para una sola cosa: que un moderador parado en el perfil publico de
     * alguien pueda saber si hay sello que revocar y sobre que identificador. El perfil
     * publico entrega el del vendedor, y las tres decisiones de este controlador van sobre
     * el de la verificacion, que hasta ahora solo se podia conseguir desde la cola de
     * pendientes. Sin esto, revocar era un endpoint al que no se podia llegar.
     *
     * <p><strong>Cuelga de aqui y no de {@code /users/{id}/verification}, que es lo que la
     * jerarquia del contrato pediria.</strong> El motivo es de seguridad y esta escrito en
     * {@code SecurityConfig}: bajo {@code /users/**} la regla generica es "autenticado", y
     * las rutas del moderador necesitan {@code hasRole("MODERATOR")}. Poner una regla
     * especifica antes tampoco sirve, porque {@code /api/v1/users/*&#47;verification}
     * casaria tambien con {@code /users/me/verification} y le quitaria a cualquier persona
     * el acceso a su propia solicitud. Bajo {@code /verifications/**} ya rige la regla
     * correcta y no hay colision posible.
     *
     * <p>Responde dos campos y nada mas. No entrega ningun dato personal, asi que no anota
     * en la bitacora: lo que RN-046 obliga a registrar es ver la cedula, la selfie o la
     * cuenta, y eso sigue pasando por {@link #imagen}.
     *
     * <p>404 cuando esa persona nunca empezo. No haber empezado no es un error.
     */
    @GetMapping("/by-seller/{sellerId}")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<SellerVerificationSummaryResponse> porVendedor(@PathVariable String sellerId) {
        return casoDeLeer
                .execute(UserId.de(sellerId))
                .map(verificacion -> ResponseEntity.ok(new SellerVerificationSummaryResponse(
                        verificacion.id().value().toString(),
                        verificacion.status().name())))
                .orElseGet(() -> ResponseEntity.notFound().build());
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

    /**
     * Revocar el sello de quien ya lo tenia. RN-013 y RN-069.
     *
     * <p>Su propio cuerpo y su propia lista cerrada, que no son los del rechazo. Hasta
     * HU-010 reutilizaba {@code RejectionReason}, cuyos valores describen una solicitud:
     * revocar por cualquier otra cosa mandaba un correo que decia "fotos ilegibles".
     */
    @PostMapping("/{id}/revocation")
    @PreAuthorize("hasRole('MODERATOR')")
    public SellerVerificationResponse revocar(
            @AuthenticationPrincipal Jwt token,
            @PathVariable String id,
            @Valid @RequestBody RevokeVerificationRequest peticion) {

        SellerVerification revocada = casoDeRevocar.execute(new RevokeVerificationCommand(
                moderadorDe(token), SellerVerificationId.de(id), revocacionDe(peticion.reason()), peticion.note()));

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

    /** Lo mismo, sobre la otra lista cerrada. RN-069: son dos y no se mezclan. */
    private static RevocationReason revocacionDe(String valor) {
        try {
            return RevocationReason.valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El motivo de la revocacion no es uno de los de la lista cerrada", e);
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
