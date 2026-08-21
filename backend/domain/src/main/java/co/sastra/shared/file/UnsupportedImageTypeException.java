package co.sastra.shared.file;

import co.sastra.shared.error.DomainException;
import co.sastra.shared.error.ErrorCode;

/**
 * Lo que se subio no es una de las imagenes aceptadas, mirando su contenido y no
 * su nombre (ADR-0018).
 *
 * <p>El mensaje interno no incluye ni el nombre del archivo ni sus bytes: es
 * entrada de quien sube, y lo que llega a un registro deja de estar bajo control.
 */
public final class UnsupportedImageTypeException extends DomainException {

    private static final long serialVersionUID = 1L;

    public UnsupportedImageTypeException() {
        super(ErrorCode.FILE_TYPE_UNSUPPORTED, "El contenido subido no es una imagen de un tipo aceptado");
    }
}
