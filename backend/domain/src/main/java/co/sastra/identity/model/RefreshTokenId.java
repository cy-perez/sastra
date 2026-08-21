package co.sastra.identity.model;

import co.sastra.shared.id.Uuid7;
import java.util.Objects;
import java.util.UUID;

/** Identificador de un token de refresco. */
public record RefreshTokenId(UUID value) {

    public RefreshTokenId {
        Objects.requireNonNull(value, "El identificador es obligatorio");
    }

    public static RefreshTokenId nuevo() {
        return new RefreshTokenId(Uuid7.nuevo());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
