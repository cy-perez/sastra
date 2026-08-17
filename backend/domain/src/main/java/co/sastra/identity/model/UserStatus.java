package co.sastra.identity.model;

/**
 * Estado de una cuenta.
 *
 * <p>"Sin verificar" no aparece aqui a proposito: no es un estado de la cuenta
 * sino la ausencia de fecha de verificacion del correo. Una cuenta recien creada
 * esta {@link #ACTIVE} y sin verificar; RN-002 la deja entrar pero sin poder
 * hacer nada mas que reenviar el correo.
 */
public enum UserStatus {
    ACTIVE,
    BLOCKED,
    /** Cierre solicitado con pedidos en curso: se completa al resolverlos (RN-009). */
    CLOSING,
    CLOSED
}
