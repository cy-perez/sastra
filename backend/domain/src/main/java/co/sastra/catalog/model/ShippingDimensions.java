package co.sastra.catalog.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Peso y dimensiones del paquete. RN-039.
 *
 * <p>Obligatorios desde el momento de publicar, aunque solo los use el cotizador de
 * la Fase 3. Si se dejaran para entonces, esa fase arrancaria con todas las
 * publicaciones existentes sin poder cotizar, y habria que perseguir a cada vendedor
 * para completarlas. Cuatro campos de friccion hoy, cero migracion de datos despues.
 *
 * <p>Es el paquete y no el producto: un vestido pesa poco y viaja en una caja. Por eso
 * vive aparte de {@link Measurements}, que describe la prenda.
 */
public record ShippingDimensions(int weightGrams, BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm) {

    /** 50 kg. Por encima no lo recoge ninguna transportadora de paqueteo. */
    private static final int GRAMOS_MAXIMOS = 50_000;

    private static final BigDecimal CM_MAXIMOS = new BigDecimal("300");

    public ShippingDimensions {
        Objects.requireNonNull(lengthCm, "El largo es obligatorio");
        Objects.requireNonNull(widthCm, "El ancho es obligatorio");
        Objects.requireNonNull(heightCm, "El alto es obligatorio");

        if (weightGrams <= 0) {
            throw new IllegalArgumentException("El peso tiene que ser positivo: " + weightGrams);
        }
        if (weightGrams > GRAMOS_MAXIMOS) {
            throw new IllegalArgumentException("El peso supera los " + GRAMOS_MAXIMOS + " gramos: " + weightGrams);
        }

        lengthCm = exigirLado(lengthCm, "largo");
        widthCm = exigirLado(widthCm, "ancho");
        heightCm = exigirLado(heightCm, "alto");
    }

    private static BigDecimal exigirLado(BigDecimal medida, String nombre) {
        if (medida.signum() <= 0) {
            throw new IllegalArgumentException("El " + nombre + " tiene que ser positivo: " + medida);
        }
        if (medida.scale() > 1) {
            throw new IllegalArgumentException("El " + nombre + " admite un decimal como maximo: " + medida);
        }
        if (medida.compareTo(CM_MAXIMOS) > 0) {
            throw new IllegalArgumentException("El " + nombre + " no es plausible: " + medida + " cm");
        }
        return medida;
    }
}
