package co.sendik.identity.config;

import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Comprobacion de contrasenas filtradas (ADR-0013).
 *
 * @param breachCheckEnabled permite apagarla. Se apaga en las pruebas de extremo
 *     a extremo y en desarrollo sin red. El minimo de diez caracteres de RN-005
 *     no depende de esto: vive en el dominio y se comprueba siempre
 * @param breachCheckTimeout corto a proposito. Si el servicio no responde rapido,
 *     el registro sigue adelante con fallo abierto en vez de hacer esperar
 * @param breachCheckApiUrl parametrizable para poder apuntar a un servidor
 *     simulado en las pruebas de integracion
 */
@Validated
@ConfigurationProperties(prefix = "sendik.password")
public record PasswordSecurityProperties(
        boolean breachCheckEnabled,
        @NotNull Duration breachCheckTimeout,
        @NotNull URI breachCheckApiUrl) {}
