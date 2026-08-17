package co.sastra.shared.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Comision que la plataforma cobra al vendedor sobre el valor del producto.
 *
 * <p>Es configurable a proposito: es un numero de negocio que puede cambiar y no
 * deberia exigir un despliegue de codigo. El valor vigente es 0.05
 * (docs/operacion/configuracion.md).
 *
 * @param rate proporcion entre 0 y 1, no un porcentaje
 */
@Validated
@ConfigurationProperties(prefix = "sastra.commission")
public record CommissionProperties(
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal rate) {}
