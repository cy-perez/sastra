package co.sastra.shared.file;

import co.sastra.shared.error.DomainException;
import co.sastra.shared.error.ErrorCode;

/**
 * La imagen no llega al minimo de pixeles que pide su politica.
 *
 * <p>El minimo depende de para que sea: RN-019 fija 900x1200 para las tomas de
 * producto y la foto de perfil tiene el suyo, mas bajo. Por eso entra por parametro
 * y no esta escrito aqui.
 *
 * <p>Se comprueba tambien en el cliente, y aun asi se comprueba aqui: lo que decide
 * el cliente lo decide quien controla el cliente.
 */
public final class ImageTooSmallException extends DomainException {

    private static final long serialVersionUID = 1L;

    public ImageTooSmallException(ImageDimensions propias, ImageDimensions minimo) {
        super(ErrorCode.FILE_DIMENSIONS_TOO_SMALL, "La imagen mide " + propias + " y el minimo es " + minimo);
    }
}
