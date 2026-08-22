package co.sastra.identity.rest;

import co.sastra.identity.dto.StartSellerVerificationCommand;
import co.sastra.identity.dto.SubmitBankAccountCommand;
import co.sastra.identity.dto.SubmitIdentityDocumentCommand;
import co.sastra.identity.dto.SubmitSelfieCommand;
import co.sastra.identity.dto.SubmitVerificationForReviewCommand;
import co.sastra.identity.model.BankAccountType;
import co.sastra.identity.model.IdentityDocumentType;
import co.sastra.identity.model.SellerVerification;
import co.sastra.identity.model.UserId;
import co.sastra.identity.rest.dto.SellerVerificationResponse;
import co.sastra.identity.rest.dto.SubmitBankAccountRequest;
import co.sastra.identity.rest.mapper.SellerVerificationResponses;
import co.sastra.identity.usecase.ReadSellerVerificationUseCase;
import co.sastra.identity.usecase.StartSellerVerificationUseCase;
import co.sastra.identity.usecase.SubmitBankAccountUseCase;
import co.sastra.identity.usecase.SubmitIdentityDocumentUseCase;
import co.sastra.identity.usecase.SubmitSelfieUseCase;
import co.sastra.identity.usecase.SubmitVerificationForReviewUseCase;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * La verificacion de vendedor, del lado de quien se verifica. HU-002.
 *
 * <p><strong>Solo existe con la bandera encendida.</strong> Sin
 * {@code FEATURE_SELLER_VERIFICATION}, el controlador no se crea y las rutas responden
 * 404: no es que rechacen, es que no estan. Es para lo que existen las banderas —
 * desplegar codigo incompleto sin exponerlo (docs/operacion/configuracion.md)— y es
 * mas honesto que un 403, que le diria a cualquiera que la funcionalidad esta ahi.
 *
 * <p>Bajo {@code /api/v1/users/me} y no bajo {@code /api/v1/sellers}: quien llama
 * todavia no es vendedor, y la ruta de un recurso no puede depender del resultado de la
 * operacion que se le pide. De paso hereda la regla de seguridad de {@code users/**},
 * que exige token.
 *
 * <p><strong>El identificador de la cuenta sale del token, nunca de la peticion.</strong>
 * Es la regla de backend/CLAUDE.md y aqui protege lo mas sensible del sistema: con un
 * identificador de la peticion, cualquiera podria subir una cedula a la solicitud de
 * otra persona.
 *
 * <p>Aqui no se valida ningun contenido de imagen. Validar en el borde dejaria la regla
 * donde no se puede probar sin HTTP; lo hacen los casos de uso, que comprueban tamano,
 * tipo por los bytes de cabecera y dimensiones (ADR-0018).
 */
@RestController
@RequestMapping("/api/v1/users/me/verification")
@ConditionalOnProperty(prefix = "sastra.features", name = "seller-verification", havingValue = "true")
public class SellerVerificationController {

    private final StartSellerVerificationUseCase casoDeIniciar;
    private final SubmitIdentityDocumentUseCase casoDeDocumento;
    private final SubmitSelfieUseCase casoDeSelfie;
    private final SubmitBankAccountUseCase casoDeCuenta;
    private final SubmitVerificationForReviewUseCase casoDeEnviar;
    private final ReadSellerVerificationUseCase casoDeLeer;

    public SellerVerificationController(
            StartSellerVerificationUseCase casoDeIniciar,
            SubmitIdentityDocumentUseCase casoDeDocumento,
            SubmitSelfieUseCase casoDeSelfie,
            SubmitBankAccountUseCase casoDeCuenta,
            SubmitVerificationForReviewUseCase casoDeEnviar,
            ReadSellerVerificationUseCase casoDeLeer) {
        this.casoDeIniciar = casoDeIniciar;
        this.casoDeDocumento = casoDeDocumento;
        this.casoDeSelfie = casoDeSelfie;
        this.casoDeCuenta = casoDeCuenta;
        this.casoDeEnviar = casoDeEnviar;
        this.casoDeLeer = casoDeLeer;
    }

    /**
     * Empieza el proceso, o devuelve el que ya iba. Criterio 1.
     *
     * <p>Responde 200 y no 201 aunque cree algo: es idempotente, y no hay una direccion
     * nueva a la que apuntar con un {@code Location} porque el recurso siempre esta en
     * esta misma ruta.
     */
    @PostMapping
    public ResponseEntity<SellerVerificationResponse> iniciar(@AuthenticationPrincipal Jwt token) {
        SellerVerification verificacion = casoDeIniciar.execute(new StartSellerVerificationCommand(usuarioDe(token)));

        return ResponseEntity.ok(SellerVerificationResponses.de(verificacion));
    }

    /**
     * El estado de la solicitud propia. Criterio 11: sin imagenes y sin numeros
     * completos.
     *
     * <p>Pasa por un caso de uso aunque sea una lectura sin reglas. Lo intente al reves
     * —el borde llamando al repositorio— y {@code ArchitectureTest} lo rechazo con razon:
     * entre el controlador y el repositorio va el caso de uso, que es quien abre la
     * transaccion y marca la frontera de la capa.
     *
     * <p>404 cuando no ha empezado, y no un cuerpo con {@code NOT_STARTED}: el recurso
     * todavia no existe, y eso es exactamente lo que significa un 404.
     */
    @GetMapping
    public ResponseEntity<SellerVerificationResponse> estado(@AuthenticationPrincipal Jwt token) {
        return casoDeLeer
                .execute(usuarioDe(token))
                .map(SellerVerificationResponses::de)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Las dos caras del documento. Criterio 2.
     *
     * <p>Multipart y no base64 dentro de un JSON: en base64 ocupan un tercio mas y
     * obligan a tener las dos imagenes en memoria dos veces.
     *
     * <p>PUT porque entregarlo otra vez sustituye lo anterior, que es lo que PUT
     * significa.
     */
    @PutMapping("/document")
    public ResponseEntity<SellerVerificationResponse> entregarDocumento(
            @AuthenticationPrincipal Jwt token,
            @RequestParam("tipo") String tipo,
            @RequestParam("numero") String numero,
            @RequestParam("titular") String titular,
            @RequestPart("frente") MultipartFile frente,
            @RequestPart("reverso") MultipartFile reverso)
            throws IOException {

        SellerVerification verificacion = casoDeDocumento.execute(new SubmitIdentityDocumentCommand(
                usuarioDe(token), tipoDeDocumento(tipo), numero, titular, frente.getBytes(), reverso.getBytes()));

        return ResponseEntity.ok(SellerVerificationResponses.de(verificacion));
    }

    /** La selfie. Criterio 3. */
    @PutMapping("/selfie")
    public ResponseEntity<SellerVerificationResponse> entregarSelfie(
            @AuthenticationPrincipal Jwt token, @RequestPart("archivo") MultipartFile archivo) throws IOException {

        SellerVerification verificacion =
                casoDeSelfie.execute(new SubmitSelfieCommand(usuarioDe(token), archivo.getBytes()));

        return ResponseEntity.ok(SellerVerificationResponses.de(verificacion));
    }

    /** La cuenta bancaria. Criterio 4. */
    @PutMapping("/bank-account")
    public ResponseEntity<SellerVerificationResponse> registrarCuenta(
            @AuthenticationPrincipal Jwt token, @Valid @RequestBody SubmitBankAccountRequest peticion) {

        SellerVerification verificacion = casoDeCuenta.execute(new SubmitBankAccountCommand(
                usuarioDe(token),
                peticion.bank(),
                tipoDeCuenta(peticion.accountType()),
                peticion.accountNumber(),
                peticion.holderName()));

        return ResponseEntity.ok(SellerVerificationResponses.de(verificacion));
    }

    /**
     * Enviar a revision. Criterio 6.
     *
     * <p>Es un POST sobre un subrecurso y no un PUT del estado: enviar no es escribir un
     * campo, es un acto que cuenta un intento de RN-014 y no se puede deshacer.
     */
    @PostMapping("/submission")
    public ResponseEntity<SellerVerificationResponse> enviarARevision(@AuthenticationPrincipal Jwt token) {
        SellerVerification verificacion =
                casoDeEnviar.execute(new SubmitVerificationForReviewCommand(usuarioDe(token)));

        return ResponseEntity.ok(SellerVerificationResponses.de(verificacion));
    }

    /**
     * El {@code sub} del token es el identificador de la cuenta.
     *
     * <p>Se usa siempre este y nunca un parametro de la peticion: es lo unico que el
     * cliente no puede elegir.
     */
    private static UserId usuarioDe(Jwt token) {
        return UserId.de(token.getSubject());
    }

    /**
     * Convierte a mano en lugar de declarar el enum en la firma.
     *
     * <p>Con el enum en la firma, un valor desconocido produce un fallo de conversion de
     * Spring que no esta mapeado y sale como 500. Convertido aqui, la
     * {@link IllegalArgumentException} que lanza {@code valueOf} ya tiene su manejador y
     * la respuesta es un error de validacion, que es lo que es.
     */
    private static IdentityDocumentType tipoDeDocumento(String valor) {
        try {
            return IdentityDocumentType.valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El tipo de documento no es CC, CE ni PPT", e);
        }
    }

    private static BankAccountType tipoDeCuenta(String valor) {
        try {
            return BankAccountType.valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El tipo de cuenta no es SAVINGS, CHECKING ni ELECTRONIC_DEPOSIT", e);
        }
    }
}
