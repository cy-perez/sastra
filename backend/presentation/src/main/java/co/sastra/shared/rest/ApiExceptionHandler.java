package co.sastra.shared.rest;

import co.sastra.identity.exception.ResendLimitReachedException;
import co.sastra.shared.error.DomainException;
import co.sastra.shared.error.ErrorCode;
import java.net.URI;
import java.time.Duration;
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
        return ResponseEntity.status(estado).body(problema);
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

    private static HttpStatus estadoDe(ErrorCode code) {
        return switch (code) {
            // 429: no es que la peticion este mal, es que llegaron demasiadas.
            case AUTH_RESEND_LIMIT_REACHED -> HttpStatus.TOO_MANY_REQUESTS;
            // 422: se entiende lo que se envio, pero el negocio lo rechaza.
            // Se llama UNPROCESSABLE_CONTENT desde la RFC 9110; el nombre
            // anterior, UNPROCESSABLE_ENTITY, esta obsoleto en Spring 7.
            case AUTH_PASSWORD_TOO_SHORT,
                    AUTH_PASSWORD_BREACHED,
                    AUTH_UNDERAGE,
                    AUTH_CONSENT_REQUIRED,
                    AUTH_VERIFICATION_TOKEN_INVALID,
                    AUTH_VERIFICATION_TOKEN_EXPIRED -> HttpStatus.UNPROCESSABLE_CONTENT;
            case COMMON_VALIDATION_FAILED -> HttpStatus.BAD_REQUEST;
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
