package co.sendik.identity.dto;

import co.sendik.identity.model.UserId;

/**
 * Cierre de cuenta con la confirmacion escrita del criterio 23.
 *
 * @param usuario sale del token, nunca de la peticion
 * @param confirmacion lo que la persona escribio. Tiene que ser su propio correo:
 *     es inequivoco, no depende del idioma de la interfaz y no se acierta por
 *     descuido, que es lo que una confirmacion escrita existe para evitar
 */
public record CloseAccountCommand(UserId usuario, String confirmacion) {}
