package co.sastra.catalog.model;

import co.sastra.shared.id.Uuid7;
import java.util.Objects;
import java.util.UUID;

/** Identificador de una categoria. */
public record CategoryId(UUID value) {

    public CategoryId {
        Objects.requireNonNull(value, "El identificador es obligatorio");
    }

    public static CategoryId nuevo() {
        return new CategoryId(Uuid7.nuevo());
    }

    public static CategoryId de(String texto) {
        Objects.requireNonNull(texto, "El identificador es obligatorio");
        try {
            return new CategoryId(UUID.fromString(texto));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El identificador no es un UUID valido", e);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
