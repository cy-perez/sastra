package co.sendik.identity.dto;

import co.sendik.identity.model.IdentityDocumentType;
import co.sendik.identity.model.UserId;

/**
 * Entregar el documento de identidad por las dos caras. Criterio 2 de HU-002.
 *
 * <p>El numero y el titular llegan como texto y los valida el dominio: es entrada
 * del usuario y no un tipo todavia.
 *
 * <p>Los dos arreglos de bytes no se copian. Son dos imagenes enteras en memoria y
 * copiarlas en cada capa se paga en un servicio que escala a cero; quien las recibe
 * no las modifica.
 *
 * @param usuario sale del token
 * @param frente la cara con la foto y el numero
 * @param reverso la otra, donde suele estar la fecha de vencimiento
 */
public record SubmitIdentityDocumentCommand(
        UserId usuario, IdentityDocumentType tipo, String numero, String titular, byte[] frente, byte[] reverso) {}
