package co.sendik.identity.model;

import java.util.Objects;

/**
 * Hash Argon2id de una contrasena.
 *
 * <p>El dominio no sabe hashear: eso es infraestructura. Lo que si le importa es
 * que a partir de aqui nadie pueda confundir un hash con una contrasena en
 * claro, que es como terminan las contrasenas en un registro de servidor.
 *
 * <p>{@link #toString()} no devuelve el valor por el mismo motivo.
 */
public record PasswordHash(String value) {

    public PasswordHash {
        Objects.requireNonNull(value, "El hash es obligatorio");
        if (value.isBlank()) {
            throw new IllegalArgumentException("El hash no puede estar vacio");
        }
    }

    @Override
    public String toString() {
        return "PasswordHash[oculto]";
    }
}
