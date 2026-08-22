package co.sastra.identity.dto;

import co.sastra.identity.model.SellerVerificationId;
import co.sastra.identity.model.UserId;

/**
 * Aprobar una verificacion. Criterio 8 de HU-002.
 *
 * @param moderador quien decide. Sale del token, nunca de la peticion: es lo que
 *     queda escrito en la bitacora y no puede venir de quien la envia
 * @param verificacion la solicitud, identificada como la ve el moderador en su bandeja
 */
public record ApproveVerificationCommand(UserId moderador, SellerVerificationId verificacion) {}
