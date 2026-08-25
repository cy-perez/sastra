package co.sendik.identity.dto;

import co.sendik.identity.model.UserId;

/**
 * Poner o reemplazar la foto de perfil. Criterio 21.
 *
 * <p>No lleva el tipo que declaro el cliente ni el nombre del archivo. Ninguno de
 * los dos decide nada —el tipo se detecta por el contenido (ADR-0018)— y un campo
 * que no se usa para nada acaba usandose para algo.
 *
 * @param usuario sale del token, nunca de la peticion
 * @param contenido los bytes tal como llegaron, sin validar. El arreglo no se
 *     copia: es una imagen entera en memoria y copiarla en cada capa se paga
 */
public record UpdateAvatarCommand(UserId usuario, byte[] contenido) {}
