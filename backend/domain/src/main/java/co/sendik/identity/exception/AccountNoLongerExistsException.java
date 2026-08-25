package co.sendik.identity.exception;

import co.sendik.shared.error.DomainException;
import co.sendik.shared.error.ErrorCode;

/**
 * El token es valido pero la cuenta a la que apunta ya no existe.
 *
 * <p>Ocurre cuando alguien cierra su cuenta: el token de acceso es un JWT y sigue
 * siendo criptograficamente valido hasta que caduca, quince minutos despues
 * (ADR-0003). Revocar los refrescos corta la renovacion, no el token que ya
 * estaba emitido.
 *
 * <p>Se responde {@code AUTH_SESSION_INVALID} porque es exactamente lo que dice:
 * esta sesion ya no sirve, vuelve a pedir credenciales. Un 500 seria mentir sobre
 * de quien es el problema, y un 200 con datos vacios seria peor.
 */
public final class AccountNoLongerExistsException extends DomainException {

    private static final long serialVersionUID = 1L;

    public AccountNoLongerExistsException() {
        super(ErrorCode.AUTH_SESSION_INVALID, "La cuenta del token ya no existe");
    }
}
