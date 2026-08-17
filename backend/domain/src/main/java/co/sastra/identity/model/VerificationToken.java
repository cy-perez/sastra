package co.sastra.identity.model;

import co.sastra.identity.exception.VerificationTokenExpiredException;
import co.sastra.identity.exception.VerificationTokenInvalidException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Token de un solo uso que viaja dentro de un enlace.
 *
 * <p>Aqui vive el hash, nunca el token. El valor en claro existe una sola vez, en
 * el caso de uso que lo genera y lo mete en el correo; quien lea la base de datos
 * despues no puede verificar una cuenta ajena.
 */
public final class VerificationToken {

    /** RN-003. */
    public static final Duration VIGENCIA_VERIFICACION_DE_CORREO = Duration.ofHours(24);

    /** RN-004. Mucho mas corta: da acceso a cambiar la contrasena. */
    public static final Duration VIGENCIA_RESTABLECIMIENTO = Duration.ofMinutes(30);

    private final VerificationTokenId id;
    private final UserId userId;
    private final TokenPurpose purpose;
    private final String tokenHash;
    private final Instant expiresAt;
    private final @Nullable Instant usedAt;
    private final Instant createdAt;

    private VerificationToken(
            VerificationTokenId id,
            UserId userId,
            TokenPurpose purpose,
            String tokenHash,
            Instant expiresAt,
            @Nullable Instant usedAt,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.purpose = Objects.requireNonNull(purpose);
        this.tokenHash = Objects.requireNonNull(tokenHash);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.usedAt = usedAt;
        this.createdAt = Objects.requireNonNull(createdAt);

        if (tokenHash.isBlank()) {
            throw new IllegalArgumentException("El hash del token no puede estar vacio");
        }
    }

    public static VerificationToken emitir(
            UserId userId, TokenPurpose purpose, String tokenHash, Instant ahora, Duration vigencia) {
        Objects.requireNonNull(ahora, "El instante de emision es obligatorio");
        Objects.requireNonNull(vigencia, "La vigencia es obligatoria");

        return new VerificationToken(
                VerificationTokenId.nuevo(), userId, purpose, tokenHash, ahora.plus(vigencia), null, ahora);
    }

    public static VerificationToken rehidratar(
            VerificationTokenId id,
            UserId userId,
            TokenPurpose purpose,
            String tokenHash,
            Instant expiresAt,
            @Nullable Instant usedAt,
            Instant createdAt) {
        return new VerificationToken(id, userId, purpose, tokenHash, expiresAt, usedAt, createdAt);
    }

    /**
     * Comprueba las dos condiciones que hacen utilizable un enlace, en este orden:
     * primero que no se haya usado y luego que no haya caducado.
     *
     * <p>El orden importa para lo que ve el usuario. Un token ya usado y ademas
     * caducado debe decir "no sirve", no "caducado": ofrecerle reenviar un enlace
     * que en realidad ya funciono lo mandaria a repetir algo que ya hizo.
     *
     * @throws VerificationTokenInvalidException si ya se uso
     * @throws VerificationTokenExpiredException si caduco (RN-003)
     */
    public void verificarUtilizable(Instant ahora) {
        Objects.requireNonNull(ahora, "El instante es obligatorio");
        if (yaSeUso()) {
            throw new VerificationTokenInvalidException();
        }
        if (estaCaducado(ahora)) {
            throw new VerificationTokenExpiredException();
        }
    }

    public boolean yaSeUso() {
        return usedAt != null;
    }

    /** El instante exacto de caducidad ya no sirve: la comparacion no es inclusiva. */
    public boolean estaCaducado(Instant ahora) {
        return !ahora.isBefore(expiresAt);
    }

    public VerificationToken marcarUsado(Instant ahora) {
        return new VerificationToken(id, userId, purpose, tokenHash, expiresAt, ahora, createdAt);
    }

    public VerificationTokenId id() {
        return id;
    }

    public UserId userId() {
        return userId;
    }

    public TokenPurpose purpose() {
        return purpose;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public @Nullable Instant usedAt() {
        return usedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    /** Sin el hash: identifica el token sin dar con que usarlo. */
    @Override
    public String toString() {
        return "VerificationToken[" + id + ", " + purpose + "]";
    }
}
