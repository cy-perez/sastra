package co.sendik.identity.model;

import co.sendik.identity.exception.AccountLockedException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Las credenciales de una cuenta y el contador que las protege (RN-006).
 *
 * <p>Estan separadas de {@link User} a proposito: un usuario cargado en memoria
 * para mostrar un nombre no debe llevar consigo con que suplantarlo. Es la misma
 * razon por la que {@code UserRepository.crear} recibe el hash aparte.
 *
 * <p>El bloqueo se cuenta <strong>desde el ultimo intento</strong>, no desde el
 * quinto. Un intento fallido mas mientras la cuenta esta bloqueada empuja el
 * desbloqueo otros 15 minutos: quien esta probando contrasenas no consigue
 * ventanas de cinco intentos cada cuarto de hora.
 */
public final class UserCredentials {

    /** RN-006. */
    public static final int INTENTOS_MAXIMOS = 5;

    /** RN-006. */
    public static final Duration BLOQUEO = Duration.ofMinutes(15);

    private final UserId userId;
    private final PasswordHash passwordHash;
    private final Instant passwordUpdatedAt;
    private final int failedAttempts;
    private final @Nullable Instant lockedUntil;

    private UserCredentials(
            UserId userId,
            PasswordHash passwordHash,
            Instant passwordUpdatedAt,
            int failedAttempts,
            @Nullable Instant lockedUntil) {
        this.userId = Objects.requireNonNull(userId);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.passwordUpdatedAt = Objects.requireNonNull(passwordUpdatedAt);
        this.failedAttempts = failedAttempts;
        this.lockedUntil = lockedUntil;

        if (failedAttempts < 0) {
            throw new IllegalArgumentException("El contador de intentos no puede ser negativo");
        }
    }

    /** Reconstruccion desde la base de datos. */
    public static UserCredentials rehidratar(
            UserId userId,
            PasswordHash passwordHash,
            Instant passwordUpdatedAt,
            int failedAttempts,
            @Nullable Instant lockedUntil) {
        return new UserCredentials(userId, passwordHash, passwordUpdatedAt, failedAttempts, lockedUntil);
    }

    /**
     * Suma un intento fallido y bloquea al llegar al maximo.
     *
     * <p>Si el bloqueo anterior ya vencio, el contador arranca de cero: quien se
     * equivoco cinco veces hace una hora tiene otros cinco intentos, no uno.
     */
    public UserCredentials registrarFallo(Instant ahora) {
        Objects.requireNonNull(ahora, "El instante es obligatorio");

        int intentos = (elBloqueoYaVencio(ahora) ? 0 : failedAttempts) + 1;
        Instant bloqueo = intentos >= INTENTOS_MAXIMOS ? ahora.plus(BLOQUEO) : null;

        return new UserCredentials(userId, passwordHash, passwordUpdatedAt, intentos, bloqueo);
    }

    /** Entrar correctamente limpia el contador y cualquier bloqueo pendiente. */
    public UserCredentials registrarExito() {
        if (failedAttempts == 0 && lockedUntil == null) {
            return this;
        }
        return new UserCredentials(userId, passwordHash, passwordUpdatedAt, 0, null);
    }

    /**
     * Cambia la contrasena y levanta cualquier bloqueo pendiente.
     *
     * <p><strong>Levantar el bloqueo de RN-006 es deliberado.</strong> Quien llega
     * hasta aqui demostro control del buzon, que es una prueba mas fuerte que la
     * propia contrasena. Mantener los quince minutos castigaria justo a la victima
     * del ataque que los provoco: cualquiera que conozca un correo puede dejar esa
     * cuenta bloqueada probando contrasenas, y restablecer es la salida natural del
     * titular.
     *
     * <p>Quien puede llamar a esto ya comprobo el token de un solo uso. Este objeto
     * no sabe de tokens y no puede comprobarlo por su cuenta.
     */
    public UserCredentials conNuevaContrasena(PasswordHash nueva, Instant ahora) {
        Objects.requireNonNull(nueva, "La contrasena nueva es obligatoria");
        Objects.requireNonNull(ahora, "El instante es obligatorio");

        return new UserCredentials(userId, nueva, ahora, 0, null);
    }

    public boolean estaBloqueada(Instant ahora) {
        Objects.requireNonNull(ahora, "El instante es obligatorio");
        return lockedUntil != null && ahora.isBefore(lockedUntil);
    }

    /**
     * @throws AccountLockedException si el bloqueo de RN-006 esta vigente
     */
    public void verificarQueNoEstaBloqueada(Instant ahora) {
        if (estaBloqueada(ahora)) {
            // No puede ser nulo: estaBloqueada acaba de comprobarlo.
            throw new AccountLockedException(Objects.requireNonNull(lockedUntil));
        }
    }

    private boolean elBloqueoYaVencio(Instant ahora) {
        return lockedUntil != null && !ahora.isBefore(lockedUntil);
    }

    public UserId userId() {
        return userId;
    }

    public PasswordHash passwordHash() {
        return passwordHash;
    }

    public Instant passwordUpdatedAt() {
        return passwordUpdatedAt;
    }

    public int failedAttempts() {
        return failedAttempts;
    }

    public @Nullable Instant lockedUntil() {
        return lockedUntil;
    }

    /** Sin el hash: {@link PasswordHash} ya se oculta, y aqui tampoco hace falta. */
    @Override
    public String toString() {
        return "UserCredentials[" + userId + ", intentos " + failedAttempts + "]";
    }
}
