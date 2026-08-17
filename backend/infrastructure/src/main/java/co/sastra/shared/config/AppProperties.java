package co.sastra.shared.config;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Direcciones publicas de la plataforma y correo de soporte.
 *
 * <p>Ninguno de estos valores se escribe en el codigo: llegan por variable de
 * entorno y se validan al arrancar. Si falta uno, la aplicacion no levanta, que
 * es preferible a descubrirlo con un usuario adentro
 * (docs/operacion/configuracion.md).
 *
 * @param baseUrl direccion publica del sitio, la que ve el comprador
 * @param apiBaseUrl direccion publica de la API
 * @param supportEmail correo al que escribe quien necesita ayuda
 * @param corsAllowedOrigins origenes con permiso para llamar a la API
 */
@Validated
@ConfigurationProperties(prefix = "sastra.app")
public record AppProperties(
        @NotNull URI baseUrl,
        @NotNull URI apiBaseUrl,
        @NotBlank @Email String supportEmail,
        @NotEmpty List<String> corsAllowedOrigins) {}
