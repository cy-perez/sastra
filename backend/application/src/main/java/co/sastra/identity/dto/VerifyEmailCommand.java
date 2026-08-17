package co.sastra.identity.dto;

/**
 * @param token el valor en claro que viajaba en el enlace del correo. Se hashea
 *     antes de consultar: la base nunca ve el original.
 */
public record VerifyEmailCommand(String token) {}
