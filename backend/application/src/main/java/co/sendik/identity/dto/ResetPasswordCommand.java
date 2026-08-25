package co.sendik.identity.dto;

/**
 * Poner una contrasena nueva con el enlace recibido por correo.
 *
 * @param token el valor en claro que llego en el enlace. Se hashea antes de
 *     tocar la base, igual que los demas: la base nunca ve el original
 * @param newPassword la contrasena nueva. Pasa por RN-005 completa, igual que en
 *     el registro: recuperar el acceso no es motivo para admitir una peor
 */
public record ResetPasswordCommand(String token, String newPassword) {}
