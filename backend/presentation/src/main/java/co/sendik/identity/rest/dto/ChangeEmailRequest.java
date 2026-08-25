package co.sendik.identity.rest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/v1/users/me/email}. Criterio 21.
 *
 * <p>Solo valida el formato. Que la direccion este libre no se comprueba aqui ni
 * se responde distinto: se responde igual este libre u ocupada, como en el
 * registro, para que el formulario no sirva de detector de cuentas.
 */
public record ChangeEmailRequest(
        @NotBlank @Email @Size(max = 254) String newEmail) {}
