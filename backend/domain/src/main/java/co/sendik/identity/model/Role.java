package co.sendik.identity.model;

/**
 * Rol de un usuario.
 *
 * <p>Un usuario tiene una sola cuenta: ser vendedor no es otra cuenta sino un rol
 * adicional que se activa al completar la verificacion
 * (docs/producto/glosario.md).
 */
public enum Role {
    BUYER,
    SELLER,
    MODERATOR,
    ADMIN
}
