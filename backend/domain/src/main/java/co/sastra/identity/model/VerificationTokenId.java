package co.sastra.identity.model;

import co.sastra.shared.id.Uuid7;
import java.util.Objects;
import java.util.UUID;

/** Identificador de un token de verificacion. */
public record VerificationTokenId(UUID value) {

    public VerificationTokenId {
        Objects.requireNonNull(value, "El identificador es obligatorio");
    }

    public static VerificationTokenId nuevo() {
        return new VerificationTokenId(Uuid7.nuevo());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
