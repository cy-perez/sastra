package co.sendik.identity.dto;

/**
 * Reenvio del correo de verificacion.
 *
 * <p>Se identifica por el token caducado y no por el correo, a proposito. Un
 * reenvio que acepta un correo cualquiera es un detector de cuentas: responder
 * distinto segun exista o no revelaria quien esta registrado, que es justo lo
 * que el criterio 2 evita en el registro.
 *
 * <p>Quien perdio el correo entero y no tiene ningun enlace entra por otra
 * puerta: inicia sesion y pide el reenvio desde el aviso de verificacion
 * pendiente (criterio 13), donde el sistema ya sabe quien es.
 */
public record ResendVerificationCommand(String expiredToken) {}
