package co.sendik.identity.rest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/v1/auth/forgot-password}.
 *
 * <p>La validacion es de formato y nada mas. Un correo con forma valida que no
 * existe llega hasta el caso de uso y termina igual que uno que si: el criterio 19
 * exige que no se pueda distinguir, y rechazarlo antes seria distinguirlo.
 */
public record ForgotPasswordRequest(
        @NotBlank @Email @Size(max = 254) String email) {}
