package co.sendik.identity.dto;

import co.sendik.identity.model.UserId;

/** Enviar la solicitud completa a revision. Criterio 6 de HU-002. */
public record SubmitVerificationForReviewCommand(UserId usuario) {}
