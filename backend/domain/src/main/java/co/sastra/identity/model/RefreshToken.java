package co.sastra.identity.model;

import co.sastra.identity.exception.RefreshTokenInvalidException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Token de refresco: la sesion larga que sostiene los tokens de acceso cortos.
 *
 * <p>Como en {@link VerificationToken}, aqui vive el hash y nunca el valor. El
 * valor en claro existe una sola vez, entre el caso de uso que lo emite y la
 * cookie del navegador; quien lea la base de datos no puede continuar la sesion
 * de nadie (ADR-0003).
 *
 * <p><strong>Rotar no es emitir otro token.</strong> RN-007 exige que el anterior
 * quede invalido en el mismo movimiento, asi que {@link #rotar} devuelve las dos
 * caras de la operacion en un {@link Rotacion} y no hay forma de guardar una sin
 * la otra. Un token consumido conserva a quien lo reemplazo, y eso es lo que
 * permite reconocer despues que alguien esta reutilizando uno viejo.
 */
public final class RefreshToken {

    /** RN-007. */
    public static final Duration VIGENCIA = Duration.ofDays(30);

    private final RefreshTokenId id;
    private final UserId userId;
    private final TokenFamilyId familyId;
    private final String tokenHash;
    private final Instant expiresAt;
    private final @Nullable Instant revokedAt;

    /** El token que salio de rotar este. Nulo mientras no se haya usado. */
    private final @Nullable RefreshTokenId replacedBy;

    /**
     * Datos con los que la persona reconoce sus propias sesiones. Son opcionales:
     * una peticion sin {@code User-Agent} es rara pero no invalida, y la IP se
     * guarda hasheada porque es un dato personal
     * (docs/operacion/datos-personales.md).
     */
    private final @Nullable String userAgent;

    private final @Nullable String ipHash;

    private final Instant createdAt;

    /** Las dos caras de una rotacion: el que se consume y el que lo sustituye. */
    public record Rotacion(RefreshToken consumido, RefreshToken emitido) {}

    private RefreshToken(
            RefreshTokenId id,
            UserId userId,
            TokenFamilyId familyId,
            String tokenHash,
            Instant expiresAt,
            @Nullable Instant revokedAt,
            @Nullable RefreshTokenId replacedBy,
            @Nullable String userAgent,
            @Nullable String ipHash,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.familyId = Objects.requireNonNull(familyId);
        this.tokenHash = Objects.requireNonNull(tokenHash);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.revokedAt = revokedAt;
        this.replacedBy = replacedBy;
        this.userAgent = userAgent;
        this.ipHash = ipHash;
        this.createdAt = Objects.requireNonNull(createdAt);

        if (tokenHash.isBlank()) {
            throw new IllegalArgumentException("El hash del token no puede estar vacio");
        }
    }

    /**
     * Primer token de una sesion nueva: abre su propia familia.
     *
     * <p>La vigencia entra por parametro y no se toma de {@link #VIGENCIA} para
     * que la configuracion pueda acortarla sin recompilar (JWT_REFRESH_TTL en
     * docs/operacion/configuracion.md). La constante sigue siendo la referencia de
     * RN-007 y el valor por omision.
     */
    public static RefreshToken abrirSesion(
            UserId userId,
            String tokenHash,
            Instant ahora,
            Duration vigencia,
            @Nullable String userAgent,
            @Nullable String ipHash) {
        return enLaFamilia(userId, TokenFamilyId.nueva(), tokenHash, ahora, vigencia, userAgent, ipHash);
    }

    private static RefreshToken enLaFamilia(
            UserId userId,
            TokenFamilyId familyId,
            String tokenHash,
            Instant ahora,
            Duration vigencia,
            @Nullable String userAgent,
            @Nullable String ipHash) {
        Objects.requireNonNull(ahora, "El instante de emision es obligatorio");
        Objects.requireNonNull(vigencia, "La vigencia es obligatoria");

        return new RefreshToken(
                RefreshTokenId.nuevo(),
                userId,
                familyId,
                tokenHash,
                ahora.plus(vigencia),
                null,
                null,
                userAgent,
                ipHash,
                ahora);
    }

    /** Reconstruccion desde la base de datos. No revalida nada. */
    public static RefreshToken rehidratar(
            RefreshTokenId id,
            UserId userId,
            TokenFamilyId familyId,
            String tokenHash,
            Instant expiresAt,
            @Nullable Instant revokedAt,
            @Nullable RefreshTokenId replacedBy,
            @Nullable String userAgent,
            @Nullable String ipHash,
            Instant createdAt) {
        return new RefreshToken(
                id, userId, familyId, tokenHash, expiresAt, revokedAt, replacedBy, userAgent, ipHash, createdAt);
    }

    /**
     * Consume este token y emite el siguiente de la misma familia (RN-007).
     *
     * <p>El nuevo token empieza a contar su vigencia desde ahora: una sesion que
     * se usa se mantiene viva, y una que se abandona caduca a los 30 dias de la
     * ultima vez que se uso.
     *
     * @throws RefreshTokenInvalidException si este token ya no es utilizable
     */
    public Rotacion rotar(
            String nuevoTokenHash,
            Instant ahora,
            Duration vigencia,
            @Nullable String nuevoUserAgent,
            @Nullable String nuevoIpHash) {
        verificarUtilizable(ahora);

        RefreshToken emitido =
                enLaFamilia(userId, familyId, nuevoTokenHash, ahora, vigencia, nuevoUserAgent, nuevoIpHash);

        RefreshToken consumido = new RefreshToken(
                id, userId, familyId, tokenHash, expiresAt, ahora, emitido.id(), userAgent, ipHash, createdAt);

        return new Rotacion(consumido, emitido);
    }

    /**
     * Comprueba que este token sirve para renovar la sesion.
     *
     * @throws RefreshTokenInvalidException si caduco, si esta revocado o si ya se
     *     uso
     */
    public void verificarUtilizable(Instant ahora) {
        Objects.requireNonNull(ahora, "El instante es obligatorio");
        if (!esUtilizable(ahora)) {
            throw new RefreshTokenInvalidException();
        }
    }

    public boolean esUtilizable(Instant ahora) {
        return !estaRevocado() && !fueReemplazado() && !estaCaducado(ahora);
    }

    /** El instante exacto de caducidad ya no sirve: la comparacion no es inclusiva. */
    public boolean estaCaducado(Instant ahora) {
        return !ahora.isBefore(expiresAt);
    }

    public boolean estaRevocado() {
        return revokedAt != null;
    }

    /**
     * Si es cierto, este token ya se roto una vez. Recibirlo de nuevo es el
     * incidente del criterio 15 y no un error corriente del cliente: o se filtro,
     * o se esta reproduciendo una peticion antigua.
     */
    public boolean fueReemplazado() {
        return replacedBy != null;
    }

    /**
     * Si este token se consumio hace menos de {@code ventana}.
     *
     * <p>Es la mitad temporal de la ventana de gracia de RN-007. Un token que se
     * roto hace medio segundo y vuelve a llegar casi nunca es una reutilizacion:
     * es la segunda de dos peticiones que salieron a la vez con la misma cookie,
     * o una que se reintenta porque la respuesta se perdio por el camino. Un
     * token robado, en cambio, se usa cuando el ladron llega, no en el mismo
     * suspiro.
     *
     * <p>El instante de consumo es {@code revokedAt}: {@link #rotar} lo pone
     * justo al reemplazarlo. Para un token que nunca se uso devuelve falso, que
     * es lo correcto: no hay carrera donde no hubo rotacion.
     */
    public boolean seConsumioDentroDe(Duration ventana, Instant ahora) {
        Objects.requireNonNull(ventana, "La ventana es obligatoria");
        Objects.requireNonNull(ahora, "El instante es obligatorio");

        Instant consumo = revokedAt;
        if (!fueReemplazado() || consumo == null) {
            return false;
        }
        return !ahora.isAfter(consumo.plus(ventana));
    }

    /** Revocar es idempotente: la primera fecha es la que vale. */
    public RefreshToken revocar(Instant ahora) {
        Objects.requireNonNull(ahora, "El instante es obligatorio");
        if (estaRevocado()) {
            return this;
        }
        return new RefreshToken(
                id, userId, familyId, tokenHash, expiresAt, ahora, replacedBy, userAgent, ipHash, createdAt);
    }

    public RefreshTokenId id() {
        return id;
    }

    public UserId userId() {
        return userId;
    }

    public TokenFamilyId familyId() {
        return familyId;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public @Nullable Instant revokedAt() {
        return revokedAt;
    }

    public @Nullable RefreshTokenId replacedBy() {
        return replacedBy;
    }

    public @Nullable String userAgent() {
        return userAgent;
    }

    public @Nullable String ipHash() {
        return ipHash;
    }

    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof RefreshToken token && id.equals(token.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /** Sin el hash y sin la IP: este texto acaba en registros del servidor. */
    @Override
    public String toString() {
        return "RefreshToken[" + id + ", familia " + familyId + "]";
    }
}
