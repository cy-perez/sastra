package co.sastra.shared.crypto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Claves de cifrado de datos sensibles (ADR-0020).
 *
 * <p><strong>Dos claves independientes, y no es ceremonia.</strong> Si el HMAC de
 * busqueda usara la clave de cifrado, filtrar una daria las dos capacidades:
 * descifrar y confirmar adivinaciones. Y adivinar es barato —una cedula colombiana
 * es un numero de ocho a diez digitos— asi que la clave de busqueda no puede
 * compartir suerte con la de cifrado.
 *
 * @param dataKeys claves de cifrado por version, en base64. Es un mapa y no una sola
 *     clave para poder rotar sin reescribir la tabla de golpe: las filas viejas
 *     siguen diciendo con que version se cifraron y su clave tiene que seguir aqui
 *     mientras exista una fila que la use
 * @param currentVersion con cual se cifra lo nuevo. Tiene que estar en {@code dataKeys}
 * @param lookupKey clave del HMAC de busqueda, en base64. Distinta de todas las de
 *     cifrado
 */
@Validated
@ConfigurationProperties(prefix = "sastra.crypto")
public record CryptoProperties(
        @NotEmpty Map<Integer, String> dataKeys,
        @Positive int currentVersion,
        @NotNull String lookupKey) {}
