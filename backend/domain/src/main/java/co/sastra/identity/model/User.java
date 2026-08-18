package co.sastra.identity.model;

import co.sastra.identity.exception.UnderageException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Una cuenta de Sastra.
 *
 * <p>Inmutable: cada cambio devuelve una instancia nueva. Con Spring Data JDBC no
 * hay sesion ni seguimiento de cambios (ADR-0004), asi que no existe la tentacion
 * de mutar un objeto y esperar que alguien lo guarde por su cuenta.
 *
 * <p>La fecha de referencia entra por parametro en lugar de leerse del reloj: es
 * lo que permite probar el cumpleanos numero 18 exacto sin esperar un ano.
 */
public final class User {

    private final UserId id;
    private final Email email;
    private final DisplayName displayName;
    private final BirthDate birthDate;

    /**
     * Perfil opcional. Nulos mientras la persona no los ponga: no se piden al
     * registrarse porque no hacen falta para tener cuenta, y un campo que no tiene
     * uso concreto no se crea (docs/operacion/datos-personales.md).
     */
    private final @Nullable City city;

    private final @Nullable Phone phone;
    private final UserLocale locale;
    private final UserStatus status;

    /** Nulo mientras no verifique. No es un estado aparte: RN-002. */
    private final @Nullable Instant emailVerifiedAt;

    private final Set<Role> roles;
    private final Instant createdAt;

    private User(
            UserId id,
            Email email,
            DisplayName displayName,
            BirthDate birthDate,
            @Nullable City city,
            @Nullable Phone phone,
            UserLocale locale,
            UserStatus status,
            @Nullable Instant emailVerifiedAt,
            Set<Role> roles,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.email = Objects.requireNonNull(email);
        this.displayName = Objects.requireNonNull(displayName);
        this.birthDate = Objects.requireNonNull(birthDate);
        this.city = city;
        this.phone = phone;
        this.locale = Objects.requireNonNull(locale);
        this.status = Objects.requireNonNull(status);
        this.emailVerifiedAt = emailVerifiedAt;
        this.roles = Set.copyOf(Objects.requireNonNull(roles));
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    /**
     * Crea una cuenta nueva, sin verificar y con el rol de comprador.
     *
     * <p>Todo el mundo entra como comprador; vender exige ademas la verificacion
     * de vendedor, que es Fase 2 (docs/producto/glosario.md).
     *
     * @throws UnderageException si no ha cumplido 18 anos (RN-008)
     */
    public static User registrar(
            UserId id,
            Email email,
            DisplayName displayName,
            BirthDate birthDate,
            UserLocale locale,
            LocalDate hoy,
            Instant ahora) {

        if (!birthDate.esMayorDeEdad(hoy) || !birthDate.esPlausible(hoy)) {
            throw new UnderageException();
        }

        return new User(
                id,
                email,
                displayName,
                birthDate,
                null,
                null,
                locale,
                UserStatus.ACTIVE,
                null,
                EnumSet.of(Role.BUYER),
                ahora);
    }

    /** Reconstruccion desde la base de datos. No revalida: el dato ya se acepto una vez. */
    public static User rehidratar(
            UserId id,
            Email email,
            DisplayName displayName,
            BirthDate birthDate,
            @Nullable City city,
            @Nullable Phone phone,
            UserLocale locale,
            UserStatus status,
            @Nullable Instant emailVerifiedAt,
            Set<Role> roles,
            Instant createdAt) {
        return new User(
                id, email, displayName, birthDate, city, phone, locale, status, emailVerifiedAt, roles, createdAt);
    }

    /**
     * Criterio 21: cambia lo que la persona puede editar de su perfil.
     *
     * <p>El correo no esta aqui y no es un olvido: cambiarlo exige verificar el
     * nuevo antes de reemplazar el anterior, asi que es otra operacion con otro
     * ritmo. La ciudad y el telefono se quitan poniendolos a nulo.
     */
    public User conPerfil(DisplayName nombre, @Nullable City ciudad, @Nullable Phone telefono) {
        Objects.requireNonNull(nombre, "El nombre es obligatorio");
        return new User(
                id, email, nombre, birthDate, ciudad, telefono, locale, status, emailVerifiedAt, roles, createdAt);
    }

    /**
     * Criterio 21: reemplaza el correo por el que se acaba de verificar.
     *
     * <p>Queda verificado en el mismo movimiento, y tiene que ser asi: la persona
     * acaba de demostrar que ese buzon es suyo abriendo el enlace. Dejarlo sin
     * verificar la obligaria a repetir el paso que acaba de dar.
     */
    public User conCorreoCambiado(Email nuevo, Instant ahora) {
        Objects.requireNonNull(nuevo, "El correo nuevo es obligatorio");
        return new User(id, nuevo, displayName, birthDate, city, phone, locale, status, ahora, roles, createdAt);
    }

    public boolean tieneElCorreoVerificado() {
        return emailVerifiedAt != null;
    }

    /**
     * Marca el correo como verificado. Es idempotente: verificar dos veces no
     * mueve la fecha, porque el enlace es de un solo uso y la primera es la
     * buena (RN-003).
     */
    public User conCorreoVerificado(Instant ahora) {
        if (tieneElCorreoVerificado()) {
            return this;
        }
        return new User(id, email, displayName, birthDate, city, phone, locale, status, ahora, roles, createdAt);
    }

    public UserId id() {
        return id;
    }

    public Email email() {
        return email;
    }

    public DisplayName displayName() {
        return displayName;
    }

    public @Nullable City city() {
        return city;
    }

    public @Nullable Phone phone() {
        return phone;
    }

    public BirthDate birthDate() {
        return birthDate;
    }

    public UserLocale locale() {
        return locale;
    }

    public UserStatus status() {
        return status;
    }

    public @Nullable Instant emailVerifiedAt() {
        return emailVerifiedAt;
    }

    public Set<Role> roles() {
        return roles;
    }

    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof User user && id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /** Sin correo ni nombre: este texto acaba en registros y no debe llevar datos personales. */
    @Override
    public String toString() {
        return "User[" + id + ", " + status + "]";
    }
}
