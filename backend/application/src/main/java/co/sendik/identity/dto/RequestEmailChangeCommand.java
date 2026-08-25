package co.sendik.identity.dto;

import co.sendik.identity.model.UserId;

/** Peticion de cambio de correo. Criterio 21. */
public record RequestEmailChangeCommand(UserId usuario, String newEmail) {}
