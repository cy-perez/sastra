package co.sastra.catalog.model;

import java.util.Locale;
import java.util.Objects;

/**
 * La talla declarada: la escala y el valor dentro de ella. RN-021.
 *
 * <p>Los dos juntos y no por separado, porque "10" no significa nada sin saber si es
 * talla numerica colombiana o pulgadas de cintura. Guardarlos en campos sueltos deja
 * que existan combinaciones que nadie quiso, como {@code ONE_SIZE} con valor "38".
 *
 * <p>El sistema tiene que ser uno de los que admite la categoria, y eso no se
 * comprueba aqui: este objeto no conoce la categoria. Lo hace {@link Product}, que ve
 * las dos cosas.
 */
public record Size(SizeSystem system, String value) {

    public Size {
        Objects.requireNonNull(system, "El sistema de talla es obligatorio");
        Objects.requireNonNull(value, "La talla es obligatoria");
        value = value.trim().toUpperCase(Locale.ROOT);

        if (!system.admite(value)) {
            throw new IllegalArgumentException("La talla " + value + " no existe en el sistema " + system);
        }
    }

    public static Size unica() {
        return new Size(SizeSystem.ONE_SIZE, "U");
    }

    @Override
    public String toString() {
        return system + " " + value;
    }
}
