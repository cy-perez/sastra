package co.sastra.identity.rest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/v1/auth/login}.
 *
 * <p>La contrasena no lleva {@code @Size(min)}: aqui no se valida si cumple la
 * politica, solo si viene. Rechazar en el borde una contrasena corta convertiria
 * el formulario de acceso en un comprobador de cuya politica cumple cada cuenta, y
 * ademas dejaria fuera a quien se registro antes de un endurecimiento de RN-005.
 */
public record LoginRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 200) String password) {}
