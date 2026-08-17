package co.sastra.identity.dto;

/**
 * @param session criterio 9: verificado el correo, la persona entra directamente.
 *     Se emite aqui y no en una segunda llamada porque el enlace es de un solo uso:
 *     si el cliente tuviera que pedir la sesion despues y esa peticion se perdiera,
 *     el token ya estaria consumido y no habria forma de recuperarla
 * @param yaEstabaVerificado permite que el borde distinga "acabas de activar la
 *     cuenta" de "esta cuenta ya estaba activa", sin que ninguno de los dos sea
 *     un error
 */
public record VerifyEmailResult(SessionResult session, boolean yaEstabaVerificado) {}
