package co.sendik.catalog.model;

import co.sendik.shared.id.Uuid7;
import java.util.Objects;
import java.util.UUID;

/** Identificador de una publicacion. */
public record ListingId(UUID value) {

    public ListingId {
        Objects.requireNonNull(value, "El identificador es obligatorio");
    }

    public static ListingId nuevo() {
        return new ListingId(Uuid7.nuevo());
    }

    public static ListingId de(String texto) {
        Objects.requireNonNull(texto, "El identificador es obligatorio");
        try {
            return new ListingId(UUID.fromString(texto));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El identificador no es un UUID valido", e);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
