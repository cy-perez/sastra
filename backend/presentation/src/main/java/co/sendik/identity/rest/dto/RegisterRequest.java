package co.sendik.identity.rest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Cuerpo de {@code POST /api/v1/auth/register}.
 *
 * <p>Tiene sus propios tipos y no reutiliza los del dominio: asi el contrato
 * publico puede quedarse quieto aunque el modelo interno cambie
 * (backend/CLAUDE.md). Una prueba de arquitectura lo comprueba.
 *
 * <p>La validacion de aqui es la primera mitad; la otra la hace el dominio. Las
 * dos, no una: esta rechaza lo que no cumple el formato, la del dominio rechaza
 * lo que no cumple la regla de negocio.
 *
 * @param acceptsTerms y {@code acceptsPrivacy} son dos casillas separadas y las
 *     dos obligatorias. Una sola para ambas no es consentimiento valido
 *     (docs/operacion/datos-personales.md). No llevan {@code @AssertTrue} a
 *     proposito: si el borde las rechazara, la respuesta seria un error de
 *     formato generico y el frontend no podria decir <em>cual</em> falta. La
 *     regla la aplica el caso de uso, que devuelve AUTH_CONSENT_REQUIRED.
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        // Sin @Size(min): el largo minimo es RN-005 y lo decide el dominio, que
        // ademas distingue "corta" de "filtrada" con codigos distintos.
        @NotBlank @Size(max = 200) String password,
        @NotBlank @Size(min = 2, max = 80) String displayName,
        @NotNull @Past LocalDate birthDate,
        String locale,
        boolean acceptsTerms,
        boolean acceptsPrivacy) {}
