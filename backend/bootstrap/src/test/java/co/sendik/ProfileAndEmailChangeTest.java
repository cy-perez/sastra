package co.sendik;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.sendik.identity.model.BirthDate;
import co.sendik.identity.model.City;
import co.sendik.identity.model.DisplayName;
import co.sendik.identity.model.Email;
import co.sendik.identity.model.Phone;
import co.sendik.identity.model.RawPassword;
import co.sendik.identity.model.TokenPurpose;
import co.sendik.identity.model.User;
import co.sendik.identity.model.UserId;
import co.sendik.identity.model.UserLocale;
import co.sendik.identity.model.VerificationToken;
import co.sendik.identity.model.VerificationTokenId;
import co.sendik.identity.port.out.CredentialsRepository;
import co.sendik.identity.port.out.PasswordHasher;
import co.sendik.identity.port.out.UserRepository;
import co.sendik.identity.port.out.VerificationTokenRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * Perfil y cambio de correo contra PostgreSQL 17 real. Criterio 21.
 *
 * <p>Aqui se comprueba lo unico que una simulacion no puede decir: <strong>que
 * columna escribe cada metodo</strong>. Es la misma leccion que dejo el
 * restablecimiento de contrasena, donde el caso de uso llamaba al metodo que
 * excluia el hash, respondia 204 y no cambiaba nada, con todas sus pruebas
 * unitarias en verde.
 */
@SpringBootTest(properties = "sendik.password.breach-check-enabled=false")
@ActiveProfiles("local")
@Import(PostgresTestContainer.class)
class ProfileAndEmailChangeTest {

    private static final String CONTRASENA = "una-contrasena-larga";

    private final UserRepository usuarios;
    private final CredentialsRepository credenciales;
    private final VerificationTokenRepository tokens;
    private final PasswordHasher hasher;
    private final JdbcClient jdbc;
    private final Clock reloj;

    private User usuario;

    ProfileAndEmailChangeTest(
            UserRepository usuarios,
            CredentialsRepository credenciales,
            VerificationTokenRepository tokens,
            PasswordHasher hasher,
            JdbcClient jdbc,
            Clock reloj) {
        this.usuarios = usuarios;
        this.credenciales = credenciales;
        this.tokens = tokens;
        this.hasher = hasher;
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    /** Correo distinto en cada prueba: el contenedor es uno para toda la clase (RN-001). */
    private User cuentaNueva() {
        User nueva = User.registrar(
                UserId.nuevo(),
                new Email("ana-" + UUID.randomUUID() + "@correo.co"),
                new DisplayName("Ana Maria"),
                new BirthDate(LocalDate.of(1990, 3, 4)),
                UserLocale.ES,
                LocalDate.now(reloj),
                reloj.instant());

        usuarios.crear(nueva, hasher.hashear(new RawPassword(CONTRASENA)));
        return nueva;
    }

    @BeforeEach
    void crearLaCuenta() {
        usuario = cuentaNueva();
    }

    private User releer() {
        return usuarios.buscarPorId(usuario.id()).orElseThrow();
    }

    // La ciudad y el telefono viajan enteros hasta la base y vuelven igual.
    @Test
    void deberia_guardar_y_releer_el_perfil_criterio_21() {
        usuarios.actualizar(usuario.conPerfil(new DisplayName("Ana"), new City("Medellin"), new Phone("3001234567")));

        User guardada = releer();
        assertThat(guardada.displayName()).isEqualTo(new DisplayName("Ana"));
        assertThat(guardada.city()).isEqualTo(new City("Medellin"));
        assertThat(guardada.phone()).isEqualTo(new Phone("3001234567"));
    }

    // Quitar un dato opcional tiene que dejar la columna nula, no la anterior.
    @Test
    void deberia_dejar_quitar_la_ciudad_y_el_telefono_criterio_21() {
        usuarios.actualizar(usuario.conPerfil(new DisplayName("Ana"), new City("Medellin"), new Phone("3001234567")));
        usuarios.actualizar(releer().conPerfil(new DisplayName("Ana"), null, null));

        assertThat(releer().city()).isNull();
        assertThat(releer().phone()).isNull();
    }

    /**
     * El metodo del perfil no escribe el correo, igual que el de credenciales no
     * escribe el hash. Si lo escribiera, cualquier guardado de perfil se saltaria
     * la verificacion del correo nuevo, que es justo lo que el criterio 21 exige.
     *
     * <p>La fecha de verificacion si la escribe, y debe hacerlo: es por donde pasa
     * {@code VerifyEmailUseCase} al consumir el enlace del registro. Lo que no puede
     * es cambiar a que direccion corresponde esa verificacion.
     */
    @Test
    void el_guardado_de_perfil_nunca_deberia_escribir_el_correo_criterio_21() {
        Email suyo = usuario.email();

        // Un objeto con el correo ya cambiado, pasado por el metodo del perfil.
        usuarios.actualizar(
                usuario.conCorreoCambiado(new Email("otra-" + UUID.randomUUID() + "@correo.co"), reloj.instant()));

        assertThat(releer().email()).isEqualTo(suyo);
    }

    /** Y el que si lo escribe, lo escribe: con la verificacion puesta y sin tocar el perfil. */
    @Test
    void el_cambio_de_correo_deberia_escribir_el_correo_y_dejarlo_verificado_criterio_21() {
        usuarios.actualizar(usuario.conPerfil(new DisplayName("Ana"), new City("Medellin"), new Phone("3001234567")));

        Email nuevo = new Email("nueva-" + UUID.randomUUID() + "@correo.co");
        usuarios.actualizarCorreo(releer().conCorreoCambiado(nuevo, reloj.instant()));

        User guardada = releer();
        assertThat(guardada.email()).isEqualTo(nuevo);
        assertThat(guardada.tieneElCorreoVerificado()).isTrue();
        assertThat(guardada.displayName()).isEqualTo(new DisplayName("Ana"));
        assertThat(guardada.city()).isEqualTo(new City("Medellin"));
        assertThat(usuarios.buscarPorCorreo(nuevo)).isPresent();
    }

    // Cambiar de correo no cambia la contrasena ni obliga a volver a entrar.
    @Test
    void el_cambio_de_correo_no_deberia_tocar_la_credencial() {
        var antes = credenciales.buscarPorUsuario(usuario.id()).orElseThrow();

        usuarios.actualizarCorreo(
                usuario.conCorreoCambiado(new Email("nueva-" + UUID.randomUUID() + "@correo.co"), reloj.instant()));

        assertThat(credenciales.buscarPorUsuario(usuario.id()).orElseThrow().passwordHash())
                .isEqualTo(antes.passwordHash());
    }

    /**
     * RN-001 la sostiene el indice unico, no solo la comprobacion del caso de uso.
     * Entre pedir el cambio y confirmarlo pasa hasta un dia, y en ese hueco dos
     * peticiones pueden pasar la comprobacion a la vez: la que llegue segunda tiene
     * que estrellarse contra la base.
     */
    @Test
    void la_base_deberia_impedir_dos_cuentas_con_el_mismo_correo_RN_001() {
        User otra = cuentaNueva();
        User conElCorreoAjeno = usuario.conCorreoCambiado(otra.email(), reloj.instant());

        assertThatThrownBy(() -> usuarios.actualizarCorreo(conElCorreoAjeno))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // El correo pendiente vive en el token: caduca con el y se consume con el.
    @Test
    void deberia_guardar_y_releer_el_correo_pendiente_del_token_criterio_21() {
        Email pendiente = new Email("pendiente-" + UUID.randomUUID() + "@correo.co");
        String hash = "hash-" + UUID.randomUUID();

        tokens.guardar(VerificationToken.paraCambioDeCorreo(
                usuario.id(), pendiente, hash, reloj.instant(), Duration.ofHours(24)));

        VerificationToken leido = tokens.buscarPorHash(hash).orElseThrow();
        assertThat(leido.purpose()).isEqualTo(TokenPurpose.EMAIL_CHANGE);
        assertThat(leido.newEmail()).isEqualTo(pendiente);
    }

    /**
     * La misma restriccion que el dominio, tambien en la base. Un token de cambio
     * sin destino no significa nada, y con destino en otro proposito significaria
     * que un enlace de verificacion sirve para cambiar la direccion de la cuenta.
     */
    @Test
    void la_base_deberia_rechazar_un_token_de_cambio_sin_destino() {
        assertThatThrownBy(() -> insertarTokenCrudo(TokenPurpose.EMAIL_CHANGE, null))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertarTokenCrudo(TokenPurpose.PASSWORD_RESET, "colada@correo.co"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Salta el dominio a proposito: lo que se comprueba es la restriccion de la
     * tabla, y el dominio no deja construir ninguno de los dos casos.
     */
    private void insertarTokenCrudo(TokenPurpose proposito, String nuevoCorreo) {
        jdbc.sql("""
                        INSERT INTO verification_tokens (id, user_id, purpose, token_hash, expires_at, created_at, new_email)
                        VALUES (:id, :usuario, :proposito, :hash, now() + interval '1 day', now(), :nuevo)
                        """)
                .param("id", VerificationTokenId.nuevo().value())
                .param("usuario", usuario.id().value())
                .param("proposito", proposito.name())
                .param("hash", "hash-" + UUID.randomUUID())
                .param("nuevo", nuevoCorreo)
                .update();
    }
}
