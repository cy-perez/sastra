package co.sastra.identity.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code POST /api/v1/auth/verify-email}. */
public record VerifyEmailRequest(@NotBlank @Size(max = 200) String token) {}
