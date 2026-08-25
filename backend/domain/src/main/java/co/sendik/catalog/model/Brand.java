package co.sendik.catalog.model;

import java.util.Objects;

/**
 * Marca del producto. Opcional y texto libre.
 *
 * <p>Libre y no lista cerrada porque mucha prenda de segunda no tiene marca legible o
 * no la tiene en absoluto, y una lista cerrada bloquearia a quien vende algo que no
 * esta en ella. Se paga en calidad de filtro, y se paga a proposito.
 */
public record Brand(String value) {

    private static final int LARGO_MAXIMO = 60;

    public Brand {
        Objects.requireNonNull(value, "La marca es obligatoria: para no tenerla, se deja sin poner");
        value = value.replaceAll("\s+", " ").trim();

        if (value.isEmpty()) {
            throw new IllegalArgumentException("La marca no puede estar vacia: para no tenerla, se deja sin poner");
        }
        if (value.length() > LARGO_MAXIMO) {
            throw new IllegalArgumentException("La marca supera los " + LARGO_MAXIMO + " caracteres");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
