package co.sastra.shared.file;

import co.sastra.shared.error.DomainException;
import co.sastra.shared.error.ErrorCode;

/**
 * RN-018: la imagen no tiene la proporcion que el catalogo exige.
 *
 * <p>3:4 no es una preferencia estetica: de ella depende que las tarjetas de la rejilla
 * tengan la misma altura. Una sola foto con otra proporcion rompe la fila entera.
 *
 * <p>Comparte codigo con las dimensiones insuficientes porque lo que quien sube tiene
 * que hacer es lo mismo, recortar de nuevo, y el asistente de captura de HU-003 recorta
 * a 3:4 de todas formas: aqui solo se comprueba que lo hizo.
 */
public final class WrongImageRatioException extends DomainException {

    private static final long serialVersionUID = 1L;

    public WrongImageRatioException(ImageDimensions dimensiones, double esperada) {
        super(
                ErrorCode.FILE_DIMENSIONS_TOO_SMALL,
                "La imagen " + dimensiones + " no respeta la proporcion " + esperada + " (RN-018)");
    }
}
