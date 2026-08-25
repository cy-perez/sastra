package co.sendik.identity.dto;

import co.sendik.identity.model.UserId;

/**
 * Empezar el proceso de verificacion. Criterio 1 de HU-002.
 *
 * @param usuario sale del token, nunca de la peticion
 */
public record StartSellerVerificationCommand(UserId usuario) {}
