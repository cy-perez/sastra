package co.sendik.identity.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code DELETE /api/v1/users/me}. Criterio 23.
 *
 * <p>Lleva cuerpo, cosa rara en un DELETE, y es a proposito: la confirmacion
 * escrita no puede ir en la direccion. Una URL acaba en el historial del
 * navegador y en el registro del servidor, y ahi el correo de la persona seria un
 * dato personal conservado sin necesidad.
 *
 * <p>Que coincida con su correo lo comprueba el dominio, no el borde: aqui solo se
 * valida que venga algo con forma razonable.
 */
public record CloseAccountRequest(@NotBlank @Size(max = 254) String confirmation) {}
