package co.sendik.identity.model;

import java.util.Objects;

/**
 * Ciudad de la persona. Dato personal de nivel publico: aparece junto a las
 * publicaciones de un vendedor (docs/operacion/datos-personales.md).
 *
 * <p>Texto libre y no una lista cerrada de municipios. Una lista obliga a
 * mantenerla y deja fuera a quien viva donde no se penso; para lo que este dato
 * hace, que es dar una idea de donde sale el producto, el texto libre basta.
 */
public record City(String value) {

    private static final int LARGO_MAXIMO = 80;

    public City {
        Objects.requireNonNull(value, "La ciudad es obligatoria");
        value = value.trim();

        if (value.isEmpty()) {
            throw new IllegalArgumentException("La ciudad no puede estar vacia: para no tenerla, se deja sin poner");
        }
        if (value.length() > LARGO_MAXIMO) {
            throw new IllegalArgumentException("La ciudad supera los " + LARGO_MAXIMO + " caracteres");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
