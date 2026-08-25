package co.sendik.identity.model;

import java.util.Objects;

/**
 * Nombre con el que la persona aparece en el sitio.
 *
 * <p>Es dato publico: lo ve cualquiera que abra una publicacion
 * (docs/operacion/datos-personales.md), asi que no admite cadena vacia ni
 * espacios de relleno que simulen un nombre.
 */
public record DisplayName(String value) {

    private static final int LARGO_MINIMO = 2;
    private static final int LARGO_MAXIMO = 80;

    public DisplayName {
        Objects.requireNonNull(value, "El nombre es obligatorio");
        value = value.trim().replaceAll("\s{2,}", " ");

        if (value.length() < LARGO_MINIMO) {
            throw new IllegalArgumentException("El nombre necesita al menos " + LARGO_MINIMO + " caracteres");
        }
        if (value.length() > LARGO_MAXIMO) {
            throw new IllegalArgumentException("El nombre supera los " + LARGO_MAXIMO + " caracteres");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
