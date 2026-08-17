package co.sastra.identity.dto;

import co.sastra.identity.model.UserId;

/**
 * Peticion de un enlace de verificacion nuevo hecha desde una sesion abierta.
 * Criterio 13.
 *
 * <p>Es la segunda puerta que anticipa {@link ResendVerificationCommand}: quien
 * perdio el correo entero y no tiene ningun enlace entra, ve el aviso de
 * verificacion pendiente y pide otro. Aqui no hace falta ocultar nada, porque el
 * identificador sale del contexto de seguridad y no de la peticion: no hay forma
 * de preguntar por una cuenta ajena.
 */
public record RequestEmailVerificationCommand(UserId userId) {}
