package co.sendik.catalog.rest.dto;

import java.math.BigDecimal;

/**
 * Peso y medidas de la caja, en las dos direcciones.
 *
 * <p>Es tambien el cuerpo de la ruta de envio: cambiarlo no pasa por moderacion, asi que
 * va por su propia ruta y no dentro de la edicion de contenido (criterio 28).
 */
public record ShippingPayload(Integer weightGrams, BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm) {}
