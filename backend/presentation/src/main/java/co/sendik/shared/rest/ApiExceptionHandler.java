package co.sendik.shared.rest;

import co.sendik.identity.exception.AccountLockedException;
import co.sendik.identity.exception.ResendLimitReachedException;
import co.sendik.shared.error.DomainException;
import co.sendik.shared.error.ErrorCode;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Traduce cualquier fallo a {@code ProblemDetail} de la RFC 9457.
 *
 * <p>Tres cosas que no salen nunca hacia afuera: el texto de la excepcion, la
 * traza y cualquier pista sobre el estado interno. Lo que sale es un
 * {@link ErrorCode} estable que el frontend traduce, y un {@code traceId} con el
 * que buscar en el registro del servidor
 * (docs/arquitectura/contrato-api.md).
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Criterio 8: cuando se agotan los reenvios, se dice cuando volver a intentar. */
    private static final Duration ESPERA_TRAS_LIMITE = Duration.ofHours(1);

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ProblemDetail> deDominio(DomainException e) {
        HttpStatus estado = estadoDe(e.code());
        String traceId = nuevoTraceId();

        LOG.info("Regla de negocio incumplida [{}] traceId={}: {}", e.code(), traceId, e.getMessage());

        ProblemDetail problema = construir(estado, e.code(), traceId);

        if (e instanceof ResendLimitReachedException) {
            return ResponseEntity.status(estado)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(ESPERA_TRAS_LIMITE.toSeconds()))
                    .body(problema);
        }
        if (e instanceof AccountLockedException bloqueada) {
            // Segundos restantes y no la hora de desbloqueo: la RFC 9110 admite las
            // dos formas, y una duracion no dice nada del reloj del servidor
            // (criterio 12).
            return ResponseEntity.status(estado)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(segundosHasta(bloqueada.desbloqueoEn())))
                    .body(problema);
        }
        return ResponseEntity.status(estado).body(problema);
    }

    /**
     * Demasiadas peticiones desde el mismo origen.
     *
     * <p>Se registra en {@code warn} y no en {@code info}: un limite que salta es
     * o un ataque o un cliente roto, y las dos cosas hay que verlas. No se
     * registra la clave, que lleva el hash del origen.
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ProblemDetail> deLimiteDeTasa(RateLimitExceededException e) {
        String traceId = nuevoTraceId();
        LOG.warn("Limite de peticiones alcanzado traceId={}", traceId);

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(
                        HttpHeaders.RETRY_AFTER,
                        String.valueOf(Math.max(e.espera().toSeconds(), 1)))
                .body(construir(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.COMMON_TOO_MANY_REQUESTS, traceId));
    }

    /** Lo que el dominio rechaza por formato despues de que el borde lo dejo pasar. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> deFormato(IllegalArgumentException e) {
        String traceId = nuevoTraceId();
        LOG.info("Entrada invalida traceId={}: {}", traceId, e.getMessage());

        return ResponseEntity.badRequest()
                .body(construir(HttpStatus.BAD_REQUEST, ErrorCode.COMMON_VALIDATION_FAILED, traceId));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> deValidacion(MethodArgumentNotValidException e) {
        String traceId = nuevoTraceId();

        List<Map<String, String>> errores = e.getBindingResult().getFieldErrors().stream()
                .map(campo -> Map.of("field", campo.getField(), "code", codigoDe(campo)))
                .toList();

        ProblemDetail problema = construir(HttpStatus.BAD_REQUEST, ErrorCode.COMMON_VALIDATION_FAILED, traceId);
        problema.setProperty("errors", errores);

        return ResponseEntity.badRequest().body(problema);
    }

    /**
     * Un recurso estatico que no existe. Es un 404, no un error del servidor.
     *
     * <p>Sin este manejador lo recogia el de {@code Exception} y respondia 500,
     * ademas de registrarlo como "Error inesperado" con su traza entera. Dos
     * problemas: al cliente le decia que el servidor esta roto cuando lo que pasa es
     * que la foto no esta, y a quien mira el registro le llenaba el nivel de error
     * con cada rastreador que prueba direcciones al azar. Lo encontro la prueba de
     * extremo a extremo que comprueba que al quitar la foto el archivo deja de
     * servirse.
     *
     * <p>Se registra en {@code debug} porque no hay nada que investigar: si alguien
     * pide un archivo que no esta, la respuesta correcta es decirlo y seguir.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> recursoInexistente(NoResourceFoundException e) {
        String traceId = nuevoTraceId();
        LOG.debug("No existe el recurso pedido traceId={}", traceId);

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(construir(HttpStatus.NOT_FOUND, ErrorCode.COMMON_NOT_FOUND, traceId));
    }

    /**
     * Cualquier otra cosa. Se registra completa con su traza para poder
     * investigarla, y se responde con lo minimo: un codigo y el mismo traceId.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> inesperado(Exception e) {
        String traceId = nuevoTraceId();
        LOG.error("Error inesperado traceId={}", traceId, e);

        return ResponseEntity.internalServerError()
                .body(construir(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.COMMON_UNEXPECTED, traceId));
    }

    /**
     * El {@code type} va como URI relativo. La RFC 9457 lo admite y evita que el
     * borde HTTP necesite conocer la direccion publica del sitio, que es
     * configuracion y vive en infrastructure, una capa que presentation no ve.
     */
    private static ProblemDetail construir(HttpStatus estado, ErrorCode code, String traceId) {
        ProblemDetail problema = ProblemDetail.forStatus(estado);
        problema.setType(URI.create("/errors/" + code.name().toLowerCase(Locale.ROOT)));
        problema.setTitle(code.name());
        problema.setProperty("code", code.name());
        problema.setProperty("traceId", traceId);
        return problema;
    }

    /** Nunca cero ni negativo: un Retry-After de 0 invita a reintentar de inmediato. */
    private static long segundosHasta(Instant momento) {
        return Math.max(Duration.between(Instant.now(), momento).toSeconds(), 1);
    }

    private static HttpStatus estadoDe(ErrorCode code) {
        return switch (code) {
            // 429: no es que la peticion este mal, es que llegaron demasiadas. El
            // bloqueo por intentos entra aqui por lo mismo: la peticion es correcta
            // y lo que sobra son los intentos.
            case AUTH_RESEND_LIMIT_REACHED, AUTH_ACCOUNT_LOCKED -> HttpStatus.TOO_MANY_REQUESTS;
            // 401: no es que falte permiso, es que la credencial no sirve. Con 403 el
            // cliente no sabria que lo que toca es volver a pedir la contrasena
            // (docs/arquitectura/contrato-api.md).
            case AUTH_INVALID_CREDENTIALS, AUTH_SESSION_INVALID -> HttpStatus.UNAUTHORIZED;
            // 403: tiene credencial valida y hasta el rol correcto, y aun asi no puede
            // hacer esto. RN-060 es el unico caso hoy: el moderador es moderador, pero
            // la solicitud es suya.
            // RN-011 y RN-013: no puede publicar porque no esta verificado, o porque
            // le revocaron el sello. Tiene sesion valida, asi que no es 401; lo que le
            // falta es una condicion suya, no un permiso sobre este recurso.
            case SELLER_SELF_REVIEW_FORBIDDEN,
                    CATALOG_SELLER_NOT_VERIFIED,
                    // RN-063, el gemelo de RN-060 en el catalogo: es moderador y la
                    // publicacion es suya. Codigo propio por lo mismo, para no dejarlo
                    // buscando un problema de permisos que no tiene.
                    CATALOG_SELF_MODERATION_FORBIDDEN -> HttpStatus.FORBIDDEN;
            // 409: la peticion es correcta y choca con el estado actual del
            // sistema, que es lo que significa un conflicto.
            //
            // Los tres de verificacion son conflictos del mismo tipo: la solicitud
            // esta bien formada y lo que no encaja es en que punto esta el proceso
            // —una transicion que RN-059 no admite, los tres intentos de RN-014 ya
            // gastados, o un documento que ya ocupa otra cuenta (RN-010)—.
            case AUTH_EMAIL_TAKEN,
                    SELLER_VERIFICATION_INVALID_STATE,
                    SELLER_VERIFICATION_ATTEMPTS_EXHAUSTED,
                    SELLER_DOCUMENT_ALREADY_VERIFIED,
                    // RN-061, y por el mismo motivo que su gemelo de verificacion: la
                    // peticion esta bien formada y lo que no encaja es en que punto
                    // esta la publicacion. Es tambien lo que devuelve el criterio 20
                    // cuando el moderador ya decidio, y el 34 cuando dos escrituras
                    // concurrentes chocan.
                    CATALOG_LISTING_INVALID_STATE -> HttpStatus.CONFLICT;
            // 422: se entiende lo que se envio, pero el negocio lo rechaza.
            // Se llama UNPROCESSABLE_CONTENT desde la RFC 9110; el nombre
            // anterior, UNPROCESSABLE_ENTITY, esta obsoleto en Spring 7.
            case AUTH_PASSWORD_TOO_SHORT,
                    AUTH_PASSWORD_BREACHED,
                    AUTH_UNDERAGE,
                    AUTH_CONSENT_REQUIRED,
                    AUTH_VERIFICATION_TOKEN_INVALID,
                    AUTH_VERIFICATION_TOKEN_EXPIRED,
                    AUTH_RESET_TOKEN_INVALID,
                    AUTH_RESET_TOKEN_EXPIRED,
                    // RN-012: los dos nombres llegaron bien escritos y lo que el
                    // negocio rechaza es que no sean el mismo. No es un conflicto de
                    // estado: es el contenido lo que no se puede aceptar.
                    SELLER_ACCOUNT_HOLDER_MISMATCH,
                    // Criterio 1: falta un paso previo que la persona ya tiene
                    // empezado. No es 403: no le falta permiso, le falta abrir un
                    // correo que ya recibio.
                    SELLER_EMAIL_NOT_VERIFIED,
                    // La entidad no esta en el catalogo. Codigo propio y no el de
                    // validacion generica: lo que hay que decirle es que elija de la
                    // lista, no que revise el formulario.
                    SELLER_UNKNOWN_INSTITUTION,
                    // RN-016, RN-017 y RN-065: faltan tomas. Lo que se envio se
                    // entiende y el negocio lo rechaza, que es la definicion de 422.
                    CATALOG_SHOTS_INCOMPLETE,
                    // RN-064: la categoria elegida no admite lo usado. No es 409
                    // —nada choca con un estado— sino contenido que no se acepta.
                    CATALOG_CONDITION_NOT_ALLOWED,
                    // RN-066: imagenes de referencia solo en tecnologia sellada.
                    CATALOG_REFERENCE_IMAGE_NOT_ALLOWED,
                    // La categoria no existe o esta retirada. Codigo propio y no el de
                    // validacion generica, por lo mismo que la entidad financiera: lo
                    // que hay que decirle es que elija otra del arbol.
                    CATALOG_UNKNOWN_CATEGORY,
                    // Criterio 6: el borrador esta incompleto para enviarlo. Guardar el
                    // borrador asi es valido, asi que no es un 400 de formato: es el
                    // negocio rechazando el envio, con la lista de campos en errors.
                    CATALOG_LISTING_INCOMPLETE,
                    // Criterio 19: el estado actual no admite editar. 422 y no 409 porque
                    // lo que sobra no es la peticion sino el momento, y el cliente no
                    // tiene que reintentar: tiene que esperar la decision.
                    CATALOG_LISTING_NOT_EDITABLE -> HttpStatus.UNPROCESSABLE_CONTENT;
            // 415: el contenido no es de un tipo que el servidor sepa manejar. Es
            // exactamente lo que significa, y le dice al cliente que el problema es
            // el formato y no lo que hay dentro. Se decide por los bytes de
            // cabecera, no por lo que declaro el cliente (ADR-0018).
            case FILE_TYPE_UNSUPPORTED -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            // 413: el tamano tiene su propio estado y conviene usarlo. Con un 400
            // generico, el cliente no puede distinguir "recorta la imagen" de
            // "revisa el formulario".
            //
            // Se llama CONTENT_TOO_LARGE desde la RFC 9110; PAYLOAD_TOO_LARGE esta
            // obsoleto en Spring 7, igual que le paso a UNPROCESSABLE_ENTITY.
            case FILE_TOO_LARGE -> HttpStatus.CONTENT_TOO_LARGE;
            // 422 y no 400: la peticion esta bien formada y el archivo es una imagen
            // valida; lo que la rechaza es una regla de negocio, RN-019.
            case FILE_DIMENSIONS_TOO_SMALL -> HttpStatus.UNPROCESSABLE_CONTENT;
            // 400 y no 422: lo que se escribio no coincide con lo que se pedia
            // escribir, que es un problema de la peticion y no del negocio.
            case AUTH_CLOSE_CONFIRMATION_MISMATCH, COMMON_VALIDATION_FAILED -> HttpStatus.BAD_REQUEST;
            case COMMON_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case COMMON_TOO_MANY_REQUESTS -> HttpStatus.TOO_MANY_REQUESTS;
            case COMMON_UNEXPECTED -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    /** El nombre de la restriccion incumplida, como codigo estable para el frontend. */
    private static String codigoDe(FieldError campo) {
        String restriccion = campo.getCode() == null ? "INVALID" : campo.getCode();
        return "VALIDATION_" + restriccion.toUpperCase(Locale.ROOT);
    }

    private static String nuevoTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
