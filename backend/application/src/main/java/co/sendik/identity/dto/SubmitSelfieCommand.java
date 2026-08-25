package co.sendik.identity.dto;

import co.sendik.identity.model.UserId;

/**
 * Entregar la selfie. Criterio 3 de HU-002.
 *
 * <p>Que se haya tomado en el momento y no venga de la galeria no se puede
 * garantizar aqui ni en ninguna parte del servidor: llegan bytes. Lo que el criterio
 * exige es que la interfaz no ofrezca el selector de archivos, y eso es de la
 * pantalla.
 */
public record SubmitSelfieCommand(UserId usuario, byte[] contenido) {}
