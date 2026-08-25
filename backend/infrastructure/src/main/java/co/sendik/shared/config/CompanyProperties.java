package co.sendik.shared.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Datos de la empresa que aparecen en documentos legales y en el pie del sitio.
 *
 * <p>Son configuracion y no constantes a proposito: hoy la operacion es como
 * persona natural y mañana puede constituirse una sociedad. Ese cambio debe ser
 * una variable de entorno, no una busqueda de texto por todo el repositorio.
 *
 * @param name razon social o nombre comercial
 * @param taxId NIT
 * @param address direccion fiscal
 */
@Validated
@ConfigurationProperties(prefix = "sendik.company")
public record CompanyProperties(
        @NotBlank String name,
        @NotBlank String taxId,
        @NotBlank String address) {}
