package co.sendik.identity.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/v1/auth/resend-verification}.
 *
 * <p>Lleva el token caducado y no el correo. Un reenvio que aceptara un correo
 * cualquiera seria un detector de cuentas.
 */
public record ResendVerificationRequest(
        @NotBlank @Size(max = 200) String expiredToken) {}
