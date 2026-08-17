package co.sastra.identity.model;

import java.util.Objects;
import java.util.UUID;

/** Identificador de un token de refresco. */
public record RefreshTokenId(UUID value) {

    public RefreshTokenId {
        Objects.requireNonNull(value, "El identificador es obligatorio");
    }

    public static RefreshTokenId nuevo() {
        return new RefreshTokenId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
