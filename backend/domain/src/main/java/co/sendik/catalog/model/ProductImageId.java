package co.sendik.catalog.model;

import co.sendik.shared.id.Uuid7;
import java.util.Objects;
import java.util.UUID;

/** Identificador de una imagen de producto. */
public record ProductImageId(UUID value) {

    public ProductImageId {
        Objects.requireNonNull(value, "El identificador es obligatorio");
    }

    public static ProductImageId nuevo() {
        return new ProductImageId(Uuid7.nuevo());
    }

    public static ProductImageId de(String texto) {
        Objects.requireNonNull(texto, "El identificador es obligatorio");
        try {
            return new ProductImageId(UUID.fromString(texto));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El identificador no es un UUID valido", e);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
