package co.sastra.catalog.model;

import java.util.Objects;

/** Titulo de la publicacion. Lo primero que se lee en la rejilla del catalogo. */
public record Title(String value) {

    private static final int LARGO_MINIMO = 5;
    private static final int LARGO_MAXIMO = 120;

    public Title {
        Objects.requireNonNull(value, "El titulo es obligatorio");
        value = normalizar(value);

        if (value.length() < LARGO_MINIMO) {
            throw new IllegalArgumentException("El titulo necesita al menos " + LARGO_MINIMO + " caracteres");
        }
        if (value.length() > LARGO_MAXIMO) {
            throw new IllegalArgumentException("El titulo supera los " + LARGO_MAXIMO + " caracteres");
        }
    }

    /**
     * Colapsa espacios y quita caracteres de control.
     *
     * <p>Lo segundo importa: un titulo con saltos de linea o caracteres invisibles
     * rompe la rejilla y sirve para colar texto que no se ve al moderar.
     */
    private static String normalizar(String texto) {
        return texto.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
    }

    @Override
    public String toString() {
        return value;
    }
}
