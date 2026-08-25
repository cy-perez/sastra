package co.sendik.identity.model;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Correo electronico de una cuenta, ya normalizado.
 *
 * <p>La normalizacion ocurre al construirlo, no al compararlo: dos objetos
 * {@code Email} iguales representan la misma cuenta, y eso hace innecesario
 * recordar un {@code toLowerCase} en cada consulta (RN-001).
 *
 * <p>No se recorta el punto de los alias tipo {@code a.n.a@gmail.com}: eso es
 * una particularidad de un proveedor concreto y tratarla como regla general
 * fusionaria cuentas legitimas en dominios que si distinguen el punto.
 */
public record Email(String value) {

    /** Limite de la RFC 5321 para la direccion completa. */
    private static final int LARGO_MAXIMO = 254;

    /**
     * Deliberadamente laxo. Validar correos con una expresion regular estricta
     * rechaza direcciones validas y no evita ninguna invalida: la unica prueba
     * real de que un correo existe es que llegue el mensaje de verificacion.
     */
    private static final Pattern FORMATO = Pattern.compile("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$");

    public Email {
        Objects.requireNonNull(value, "El correo es obligatorio");
        value = value.trim().toLowerCase(Locale.ROOT);

        if (value.length() > LARGO_MAXIMO) {
            throw new IllegalArgumentException("El correo supera los " + LARGO_MAXIMO + " caracteres");
        }
        if (!FORMATO.matcher(value).matches()) {
            throw new IllegalArgumentException("El correo no tiene un formato valido");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
