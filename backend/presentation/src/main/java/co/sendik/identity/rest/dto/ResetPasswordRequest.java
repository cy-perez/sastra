package co.sendik.identity.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/v1/auth/reset-password}.
 *
 * <p>El borde no aplica el minimo de diez caracteres de RN-005. Lo hace el
 * dominio, que devuelve {@code AUTH_PASSWORD_TOO_SHORT} y permite decirle a la
 * persona cual de las dos reglas incumplio; un {@code @Size(min = 10)} aqui daria
 * un error de validacion generico y perderia esa distincion. El maximo si va, y
 * es una defensa: sin el, una cadena de megabytes se hashea con Argon2id.
 */
public record ResetPasswordRequest(
        @NotBlank @Size(max = 200) String token,
        @NotBlank @Size(max = 200) String newPassword) {}
