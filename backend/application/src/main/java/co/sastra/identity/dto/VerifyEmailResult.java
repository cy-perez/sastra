package co.sastra.identity.dto;

import co.sastra.identity.model.UserId;

/**
 * @param yaEstabaVerificado permite que el borde distinga "acabas de activar la
 *     cuenta" de "esta cuenta ya estaba activa", sin que ninguno de los dos sea
 *     un error.
 */
public record VerifyEmailResult(UserId userId, String email, boolean yaEstabaVerificado) {}
