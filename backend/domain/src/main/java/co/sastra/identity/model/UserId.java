package co.sastra.identity.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Identificador de una cuenta.
 *
 * <p>Tipado a proposito y no un {@code UUID} suelto: una firma que recibe dos
 * UUID admite que se pasen en el orden equivocado y compila igual
 * (backend/CLAUDE.md).
 */
public record UserId(UUID value) {

    public UserId {
        Objects.requireNonNull(value, "El identificador es obligatorio");
    }

    public static UserId nuevo() {
        return new UserId(UUID.randomUUID());
    }

    public static UserId de(String texto) {
        Objects.requireNonNull(texto, "El identificador es obligatorio");
        try {
            return new UserId(UUID.fromString(texto));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El identificador no es un UUID valido", e);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
