package co.sendik.catalog.rest.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Peso y medidas de la caja, en las dos direcciones.
 *
 * <p>Los cuatro son obligatorios: media caja no es una caja. Cuando llega dentro del
 * producto, el borrador puede no traer envio ninguno —el objeto entero va nulo—, pero
 * traerlo a medias no.
 *
 * <p>Es tambien el cuerpo de la ruta de envio: cambiarlo no pasa por moderacion, asi que
 * va por su propia ruta y no dentro de la edicion de contenido (criterio 28).
 */
public record ShippingPayload(
        @NotNull @Positive Integer weightGrams,
        @NotNull @Positive BigDecimal lengthCm,
        @NotNull @Positive BigDecimal widthCm,
        @NotNull @Positive BigDecimal heightCm) {}
