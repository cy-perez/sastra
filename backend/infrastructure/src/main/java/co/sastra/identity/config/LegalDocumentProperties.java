package co.sastra.identity.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Version vigente de cada documento legal.
 *
 * <p>Es configuracion y no una constante porque cambia cada vez que se publica un
 * texto nuevo, y una version equivocada invalida la prueba del consentimiento:
 * quedaria escrito que la persona acepto algo que nunca vio
 * (docs/operacion/datos-personales.md).
 */
@Validated
@ConfigurationProperties(prefix = "sastra.legal")
public record LegalDocumentProperties(
        @NotBlank String termsVersion, @NotBlank String privacyVersion) {}
