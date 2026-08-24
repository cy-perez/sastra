package co.sastra.catalog.exception;

import co.sastra.shared.error.DomainException;
import co.sastra.shared.error.ErrorCode;

/**
 * RN-066: solo la tecnologia declarada sellada admite imagenes de referencia.
 *
 * <p>En moda no existen. El sitio promete que lo que se ve es la pieza exacta que se
 * recibe, y una foto de catalogo del fabricante en una prenda de segunda convierte
 * esa frase en publicidad enganosa.
 */
public final class ReferenceImageNotAllowedException extends DomainException {

    private static final long serialVersionUID = 1L;

    public ReferenceImageNotAllowedException(String motivo) {
        super(ErrorCode.CATALOG_REFERENCE_IMAGE_NOT_ALLOWED, "Imagen de referencia no admitida: " + motivo);
    }
}
