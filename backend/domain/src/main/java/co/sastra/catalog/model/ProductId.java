package co.sastra.catalog.model;

import co.sastra.shared.id.Uuid7;
import java.util.Objects;
import java.util.UUID;

/** Identificador de un producto. */
public record ProductId(UUID value) {

    public ProductId {
        Objects.requireNonNull(value, "El identificador es obligatorio");
    }

    public static ProductId nuevo() {
        return new ProductId(Uuid7.nuevo());
    }

    public static ProductId de(String texto) {
        Objects.requireNonNull(texto, "El identificador es obligatorio");
        try {
            return new ProductId(UUID.fromString(texto));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El identificador no es un UUID valido", e);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
