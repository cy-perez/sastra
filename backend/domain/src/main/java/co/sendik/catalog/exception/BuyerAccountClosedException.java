package co.sendik.catalog.exception;

import co.sendik.shared.error.DomainException;
import co.sendik.shared.error.ErrorCode;

/**
 * El token es valido pero la cuenta que lo pidio ya no existe. HU-011.
 *
 * <p>Es el gemelo de {@code AccountNoLongerExistsException} de {@code identity}, y lleva su
 * mismo codigo a proposito: hacia afuera es exactamente la misma situacion —esta sesion ya
 * no sirve, vuelve a pedir credenciales— y quien la recibe no tiene por que saber que
 * contexto la levanto.
 *
 * <p>No se reutiliza aquella porque vive en el dominio de otro contexto, y un contexto no
 * importa el modelo de otro. El codigo si se comparte, porque {@code ErrorCode} es de
 * {@code shared} y es el catalogo publico de la API.
 */
public final class BuyerAccountClosedException extends DomainException {

    private static final long serialVersionUID = 1L;

    public BuyerAccountClosedException() {
        super(ErrorCode.AUTH_SESSION_INVALID, "La cuenta del token ya no existe");
    }
}
