package co.sastra.catalog.model;

import java.util.Objects;

/**
 * Descripcion del producto.
 *
 * <p>Se guarda como texto plano y el frontend nunca la interpreta como marcado. Aqui
 * solo se quitan los caracteres de control, que no aportan nada y sirven para
 * esconder texto de quien modera; los saltos de linea si se conservan, porque una
 * descripcion larga sin parrafos no se lee.
 */
public record Description(String value) {

    private static final int LARGO_MAXIMO = 4000;

    public Description {
        Objects.requireNonNull(value, "La descripcion es obligatoria");
        value = value.replaceAll("[\\p{Cntrl}&&[^\\n]]", " ").strip();

        if (value.isEmpty()) {
            throw new IllegalArgumentException("La descripcion no puede estar vacia");
        }
        if (value.length() > LARGO_MAXIMO) {
            throw new IllegalArgumentException("La descripcion supera los " + LARGO_MAXIMO + " caracteres");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
