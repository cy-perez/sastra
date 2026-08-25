package co.sendik.identity.rest.dto;

/**
 * Respuesta de la verificacion del correo.
 *
 * <p>Lleva la sesion porque el criterio 9 de HU-001 pide que la persona entre
 * directamente: quien abre el enlace queda dentro, sin escribir la contrasena otra
 * vez. El token de refresco viaja en la cookie, como en el ingreso.
 *
 * @param alreadyVerified permite distinguir "acabas de activarla" de "ya estaba
 *     activa" sin que ninguno de los dos sea un error. Los dos entran igual
 */
public record VerifyEmailResponse(SessionResponse session, boolean alreadyVerified) {}
