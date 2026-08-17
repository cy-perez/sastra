package co.sastra.identity.model;

import java.util.Objects;
import java.util.UUID;

/** Identificador de un consentimiento. */
public record ConsentId(UUID value) {

    public ConsentId {
        Objects.requireNonNull(value, "El identificador es obligatorio");
    }

    public static ConsentId nuevo() {
        return new ConsentId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
