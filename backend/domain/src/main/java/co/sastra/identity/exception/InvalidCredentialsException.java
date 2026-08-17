package co.sastra.identity.exception;

import co.sastra.shared.error.DomainException;
import co.sastra.shared.error.ErrorCode;

/**
 * Criterio 11 de HU-001: el correo no existe, o la contrasena no coincide.
 *
 * <p>Es una sola excepcion para las dos causas a proposito. El mensaje interno
 * tampoco las distingue: acaba en el registro del servidor, y ahi tener una linea
 * que diga "ese correo no existe" es un inventario de cuentas para quien lea los
 * registros.
 */
public final class InvalidCredentialsException extends DomainException {

    private static final long serialVersionUID = 1L;

    public InvalidCredentialsException() {
        super(ErrorCode.AUTH_INVALID_CREDENTIALS, "Las credenciales no son validas");
    }
}
