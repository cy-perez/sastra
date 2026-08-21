package co.sastra.shared.crypto;

import java.util.Objects;

/**
 * Un dato sensible ya cifrado, con la version de clave que lo cifro (ADR-0020).
 *
 * <p>Las dos partes viajan juntas siempre, y por eso son un tipo y no dos columnas
 * sueltas: un texto cifrado sin su version de clave no se puede descifrar nunca
 * mas, y es la clase de fila que se descubre el dia que hay que leerla. La base
 * repite la misma exigencia con una restriccion, porque el tipo no protege de un
 * {@code UPDATE} escrito a mano.
 *
 * <p>La version existe para poder rotar la clave sin reescribir toda la tabla de
 * golpe: las filas viejas siguen diciendo con que se cifraron.
 *
 * <p>Vive en {@code domain} y no junto al adaptador porque es parte de la forma del
 * dato, no del mecanismo. Aqui no hay ni una llamada de criptografia.
 */
public record EncryptedValue(String cipher, int keyVersion) {

    public EncryptedValue {
        Objects.requireNonNull(cipher, "El texto cifrado es obligatorio");

        if (cipher.isBlank()) {
            throw new IllegalArgumentException("El texto cifrado no puede venir vacio");
        }
        if (keyVersion <= 0) {
            throw new IllegalArgumentException("La version de la clave empieza en 1, y llego " + keyVersion);
        }
    }

    /**
     * No devuelve el texto cifrado.
     *
     * <p>No es que el cifrado sea secreto —para eso esta cifrado— sino que un
     * {@code toString} util aqui invita a interpolar el objeto en un registro, y de
     * ahi a interpolar el valor en claro hay un paso. El tipo no ayuda a dar ese paso.
     */
    @Override
    public String toString() {
        return "EncryptedValue[v" + keyVersion + "]";
    }
}
