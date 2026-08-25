package co.sendik.shared.error;

import java.util.Objects;

/**
 * Base de las excepciones de negocio.
 *
 * <p>Lleva un {@link ErrorCode} estable en lugar de un mensaje para el usuario.
 * El texto de la excepcion existe para el registro del servidor y para quien
 * depura; nunca sale hacia la API (docs/arquitectura/contrato-api.md).
 */
public abstract class DomainException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode code;

    protected DomainException(ErrorCode code, String mensajeInterno) {
        super(mensajeInterno);
        this.code = Objects.requireNonNull(code, "El codigo de error es obligatorio");
    }

    public ErrorCode code() {
        return code;
    }
}
