package co.sastra.identity.dto;

import co.sastra.identity.model.UserId;

/** Peticion de cambio de correo. Criterio 21. */
public record RequestEmailChangeCommand(UserId usuario, String newEmail) {}
