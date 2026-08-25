package co.sendik.shared.file;

import co.sendik.shared.error.DomainException;
import co.sendik.shared.error.ErrorCode;

/** La imagen pasa del tamano maximo que acepta su almacen. */
public final class ImageTooLargeException extends DomainException {

    private static final long serialVersionUID = 1L;

    public ImageTooLargeException(long bytes, long maximo) {
        super(ErrorCode.FILE_TOO_LARGE, "La imagen ocupa " + bytes + " bytes y el maximo es " + maximo);
    }
}
