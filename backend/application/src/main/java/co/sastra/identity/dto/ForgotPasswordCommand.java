package co.sastra.identity.dto;

/**
 * Peticion de enlace para restablecer la contrasena.
 *
 * <p>Solo el correo. No lleva nada mas porque no hay nada mas que comprobar:
 * quien pide esto todavia no ha demostrado ser nadie, y la prueba llega despues,
 * al abrir el enlace que solo puede leer quien tenga ese buzon.
 */
public record ForgotPasswordCommand(String email) {}
