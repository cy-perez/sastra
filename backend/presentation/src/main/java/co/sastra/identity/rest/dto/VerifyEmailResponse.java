package co.sastra.identity.rest.dto;

/**
 * Respuesta de la verificacion.
 *
 * <p>No lleva sesion. El criterio 9 de HU-001 pide que la persona entre
 * directamente, y eso exige emitir tokens: llega con la rebanada B. Aqui la
 * cuenta queda activa y el frontend manda a iniciar sesion.
 *
 * @param alreadyVerified permite distinguir "acabas de activarla" de "ya estaba
 *     activa" sin que ninguno de los dos sea un error.
 */
public record VerifyEmailResponse(String email, boolean alreadyVerified) {}
