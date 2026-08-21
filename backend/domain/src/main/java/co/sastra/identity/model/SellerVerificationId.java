package co.sastra.identity.model;

import co.sastra.shared.id.Uuid7;
import java.util.Objects;
import java.util.UUID;

/** Identificador de una solicitud de verificacion. */
public record SellerVerificationId(UUID value) {

    public SellerVerificationId {
        Objects.requireNonNull(value, "El identificador es obligatorio");
    }

    public static SellerVerificationId nuevo() {
        return new SellerVerificationId(Uuid7.nuevo());
    }

    public static SellerVerificationId de(String texto) {
        Objects.requireNonNull(texto, "El identificador es obligatorio");
        try {
            return new SellerVerificationId(UUID.fromString(texto));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El identificador no es un UUID valido", e);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
