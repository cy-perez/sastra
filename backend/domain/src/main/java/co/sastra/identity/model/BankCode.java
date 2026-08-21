package co.sastra.identity.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Referencia estable a una entidad financiera.
 *
 * <p>Un codigo y no el nombre. Las entidades se fusionan y se renombran —Scotiabank
 * y Colpatria son hoy una sola— y el nombre es lo que cambia; guardarlo en la fila
 * del vendedor obligaria a reescribir filas cada vez que eso pasa. Lo que la
 * pantalla muestra sale de la tabla de entidades, que es donde vive el nombre
 * (docs/producto/historias/HU-002-verificacion-de-vendedor.md).
 *
 * <p>El dominio no conoce la lista. Que exista una entidad con este codigo lo
 * comprueba quien tiene la tabla delante, en {@code infrastructure}: una lista de
 * veintinueve nombres en una enumeracion de {@code domain} obligaria a desplegar
 * codigo para agregar un banco.
 */
public record BankCode(String value) {

    /** Minusculas, digitos y guiones. La forma de un identificador, no de un nombre. */
    private static final Pattern VALIDO = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    private static final int LARGO_MAXIMO = 40;

    public BankCode {
        Objects.requireNonNull(value, "El codigo de la entidad es obligatorio");
        value = value.trim();

        if (value.length() > LARGO_MAXIMO) {
            throw new IllegalArgumentException("El codigo de la entidad supera los " + LARGO_MAXIMO + " caracteres");
        }
        if (!VALIDO.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "El codigo de la entidad solo admite minusculas, digitos y guiones: " + value);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
