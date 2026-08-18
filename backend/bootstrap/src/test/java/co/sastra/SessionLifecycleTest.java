package co.sastra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.sastra.identity.dto.LoginCommand;
import co.sastra.identity.dto.LogoutCommand;
import co.sastra.identity.dto.RefreshSessionCommand;
import co.sastra.identity.dto.SessionResult;
import co.sastra.identity.exception.AccountLockedException;
import co.sastra.identity.exception.InvalidCredentialsException;
import co.sastra.identity.exception.RefreshTokenInvalidException;
import co.sastra.identity.model.BirthDate;
import co.sastra.identity.model.DisplayName;
import co.sastra.identity.model.Email;
import co.sastra.identity.model.PasswordHash;
import co.sastra.identity.model.RawPassword;
import co.sastra.identity.model.RefreshToken;
import co.sastra.identity.model.User;
import co.sastra.identity.model.UserCredentials;
import co.sastra.identity.model.UserId;
import co.sastra.identity.model.UserLocale;
import co.sastra.identity.port.out.AccessTokenIssuer;
import co.sastra.identity.port.out.CredentialsRepository;
import co.sastra.identity.port.out.MailSender;
import co.sastra.identity.port.out.PasswordHasher;
import co.sastra.identity.port.out.RefreshTokenRepository;
import co.sastra.identity.port.out.TokenGenerator;
import co.sastra.identity.port.out.UserRepository;
import co.sastra.identity.usecase.LoginUseCase;
import co.sastra.identity.usecase.LogoutUseCase;
import co.sastra.identity.usecase.RefreshSessionUseCase;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * Rotacion, revocacion y bloqueo contra PostgreSQL 17 real. HU-001 rebanada B.
 *
 * <p>Estas reglas no se pueden dar por buenas con simulaciones. Tres de ellas
 * dependen de como se comporta la base de datos y de donde estan las fronteras de
 * transaccion, que es justo lo que un {@code mock} no reproduce:
 *
 * <ul>
 *   <li>Que la rotacion sea atomica (RN-007).
 *   <li>Que el contador de intentos <strong>sobreviva</strong> al rechazo del
 *       ingreso. Si el caso de uso abriera transaccion, la excepcion la revertiria y
 *       RN-006 no bloquearia nunca, con todas sus pruebas unitarias en verde.
 *   <li>Que revocar una familia y lanzar la excepcion del criterio 15 no se anulen
 *       entre si.
 * </ul>
 *
 * <p>La cuenta se crea por el repositorio y no por el caso de uso de registro: lo que
 * se prueba aqui es la sesion, y pasar por el registro traeria el correo y la
 * comprobacion de contrasenas filtradas sin necesidad.
 */
@SpringBootTest(properties = "sastra.password.breach-check-enabled=false")
@ActiveProfiles("local")
@Import(PostgresTestContainer.class)
class SessionLifecycleTest {

    private static final String CONTRASENA = "una-contrasena-larga";

    private final UserRepository usuarios;
    private final CredentialsRepository credenciales;
    private final RefreshTokenRepository refrescos;
    private final PasswordHasher hasher;
    private final TokenGenerator generadorDeTokens;
    private final LoginUseCase ingreso;
    private final RefreshSessionUseCase refresco;

    /**
     * El mismo caso de uso con la ventana de gracia de RN-007 en cero.
     *
     * <p>Existe porque las pruebas del criterio 15 tienen que reutilizar un token
     * <em>despues</em> de la ventana, y el bean real la trae en diez segundos: sin
     * esto habria que dormir la prueba diez segundos o esperar a que el reloj
     * avance, que es la clase de prueba que un dia falla sola. Con cero, dos
     * llamadas seguidas ya estan fuera de la ventana y se prueba lo que se queria
     * probar. La ventana en si se prueba con el bean real, justo debajo.
     */
    private final RefreshSessionUseCase refrescoSinGracia;

    private final LogoutUseCase cierre;
    private final JdbcClient jdbc;
    private final Clock reloj;

    private User usuario;

    SessionLifecycleTest(
            UserRepository usuarios,
            CredentialsRepository credenciales,
            RefreshTokenRepository refrescos,
            PasswordHasher hasher,
            TokenGenerator generadorDeTokens,
            AccessTokenIssuer accesos,
            MailSender correo,
            LoginUseCase ingreso,
            RefreshSessionUseCase refresco,
            LogoutUseCase cierre,
            JdbcClient jdbc,
            Clock reloj) {
        this.usuarios = usuarios;
        this.credenciales = credenciales;
        this.refrescos = refrescos;
        this.hasher = hasher;
        this.generadorDeTokens = generadorDeTokens;
        this.ingreso = ingreso;
        this.refresco = refresco;
        this.refrescoSinGracia = new RefreshSessionUseCase(
                refrescos, usuarios, accesos, generadorDeTokens, correo, RefreshToken.VIGENCIA, Duration.ZERO, reloj);
        this.cierre = cierre;
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    @BeforeEach
    void crearLaCuenta() {
        // Correo distinto en cada prueba: el contenedor es uno para toda la clase y
        // RN-001 no admite dos cuentas con el mismo correo.
        String correo = "ana-" + UUID.randomUUID() + "@correo.co";

        usuario = User.registrar(
                UserId.nuevo(),
                new Email(correo),
                new DisplayName("Ana Maria"),
                new BirthDate(LocalDate.of(1990, 3, 4)),
                UserLocale.ES,
                LocalDate.now(reloj),
                reloj.instant());

        usuarios.crear(usuario, hasher.hashear(new RawPassword(CONTRASENA)));
    }

    private SessionResult entrar() {
        return ingreso.execute(new LoginCommand(usuario.email().value(), CONTRASENA, "Firefox", "ip-hash"));
    }

    private void intentarConContrasenaIncorrecta() {
        assertThatThrownBy(() -> ingreso.execute(
                        new LoginCommand(usuario.email().value(), "no-es-la-contrasena", "Firefox", "ip-hash")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void deberia_abrir_una_sesion_y_guardar_solo_el_hash_del_refresco() {
        SessionResult sesion = entrar();

        assertThat(sesion.accessToken()).isNotBlank();
        assertThat(sesion.refreshToken()).isNotBlank();
        assertThat(sesion.user().email()).isEqualTo(usuario.email().value());

        long conElValorEnClaro = jdbc.sql("SELECT count(*) FROM refresh_tokens WHERE token_hash = :valor")
                .param("valor", sesion.refreshToken())
                .query(Long.class)
                .single();
        assertThat(conElValorEnClaro).isZero();

        assertThat(refrescos.buscarPorHash(generadorDeTokens.hashearRecibido(sesion.refreshToken())))
                .isPresent();
    }

    // Criterio 14 y RN-007: el anterior queda invalido en el mismo movimiento.
    @Test
    void deberia_rotar_el_refresco_y_dejar_el_anterior_inservible_RN_007() {
        SessionResult primera = entrar();

        SessionResult segunda =
                refresco.execute(new RefreshSessionCommand(primera.refreshToken(), "Chrome", "otra-ip"));

        assertThat(segunda.refreshToken()).isNotEqualTo(primera.refreshToken());

        RefreshToken consumido = buscar(primera.refreshToken());
        RefreshToken vigente = buscar(segunda.refreshToken());

        assertThat(consumido.fueReemplazado()).isTrue();
        assertThat(consumido.replacedBy()).isEqualTo(vigente.id());
        assertThat(consumido.esUtilizable(reloj.instant())).isFalse();
        assertThat(vigente.esUtilizable(reloj.instant())).isTrue();
        // La familia es la sesion: rotar no abre una nueva.
        assertThat(vigente.familyId()).isEqualTo(consumido.familyId());
    }

    @Test
    void deberia_poder_rotar_varias_veces_seguidas() {
        String enMano = entrar().refreshToken();

        for (int i = 0; i < 3; i++) {
            enMano = refresco.execute(new RefreshSessionCommand(enMano, "Chrome", null))
                    .refreshToken();
        }

        assertThat(buscar(enMano).esUtilizable(reloj.instant())).isTrue();
        assertThat(vivosEnLaFamiliaDe(enMano)).isEqualTo(1);
    }

    // Criterio 15: el token reutilizado revoca la cadena completa. Y la revocacion
    // tiene que quedar escrita aunque la peticion termine en excepcion.
    @Test
    void deberia_revocar_la_familia_completa_ante_un_refresco_reutilizado_criterio_15() {
        SessionResult primera = entrar();
        SessionResult segunda =
                refrescoSinGracia.execute(new RefreshSessionCommand(primera.refreshToken(), "Chrome", null));

        assertThatThrownBy(() ->
                        refrescoSinGracia.execute(new RefreshSessionCommand(primera.refreshToken(), "Chrome", null)))
                .isInstanceOf(RefreshTokenInvalidException.class);

        assertThat(buscar(segunda.refreshToken()).estaRevocado()).isTrue();
        assertThat(vivosEnLaFamiliaDe(segunda.refreshToken())).isZero();

        // Y la sesion queda muerta de verdad: el token que era valido ya no sirve.
        assertThatThrownBy(() ->
                        refrescoSinGracia.execute(new RefreshSessionCommand(segunda.refreshToken(), "Chrome", null)))
                .isInstanceOf(RefreshTokenInvalidException.class);
    }

    /**
     * Ventana de gracia de RN-007, contra la base de datos real.
     *
     * <p>Es el caso de las dos pestanas: las dos arrancan con la misma cookie, la
     * primera rota y la segunda llega con el token que acaba de consumirse. Se le
     * dice que no, pero no se cierra la sesion ni se manda ningun aviso: el token
     * que salio de la rotacion sigue vivo y la persona puede seguir donde estaba.
     */
    @Test
    void no_deberia_cerrar_la_sesion_por_una_carrera_entre_dos_pestanas_RN_007() {
        SessionResult primera = entrar();
        SessionResult segunda = refresco.execute(new RefreshSessionCommand(primera.refreshToken(), "Chrome", null));

        assertThatThrownBy(() -> refresco.execute(new RefreshSessionCommand(primera.refreshToken(), "Chrome", null)))
                .isInstanceOf(RefreshTokenInvalidException.class);

        assertThat(buscar(segunda.refreshToken()).esUtilizable(reloj.instant())).isTrue();
        assertThat(vivosEnLaFamiliaDe(segunda.refreshToken())).isEqualTo(1);

        // Y la sesion sigue sirviendo: la pestana que gano la carrera continua.
        assertThat(refresco.execute(new RefreshSessionCommand(segunda.refreshToken(), "Chrome", null))
                        .refreshToken())
                .isNotBlank();
    }

    @Test
    void no_deberia_afectar_a_las_demas_sesiones_de_la_misma_persona_criterio_15() {
        SessionResult enElPortatil = entrar();
        SessionResult enElMovil = entrar();
        refrescoSinGracia.execute(new RefreshSessionCommand(enElPortatil.refreshToken(), "Chrome", null));

        assertThatThrownBy(() -> refrescoSinGracia.execute(
                        new RefreshSessionCommand(enElPortatil.refreshToken(), "Chrome", null)))
                .isInstanceOf(RefreshTokenInvalidException.class);

        assertThat(buscar(enElMovil.refreshToken()).esUtilizable(reloj.instant()))
                .isTrue();
    }

    // Criterio 16: se revoca en el servidor, no solo en el navegador.
    @Test
    void deberia_revocar_la_sesion_al_cerrarla_criterio_16() {
        SessionResult sesion = entrar();

        cierre.execute(new LogoutCommand(sesion.refreshToken()));

        assertThat(buscar(sesion.refreshToken()).estaRevocado()).isTrue();
        assertThatThrownBy(() -> refresco.execute(new RefreshSessionCommand(sesion.refreshToken(), "Chrome", null)))
                .isInstanceOf(RefreshTokenInvalidException.class);
    }

    @Test
    void no_deberia_fallar_al_cerrar_una_sesion_que_no_existe() {
        cierre.execute(new LogoutCommand("un-token-inventado"));
        cierre.execute(new LogoutCommand(null));
    }

    /**
     * La prueba que justifica que el ingreso no abra transaccion: el contador tiene
     * que seguir ahi despues de que la peticion termine en error.
     */
    @Test
    void deberia_conservar_el_contador_de_intentos_tras_un_ingreso_rechazado_RN_006() {
        intentarConContrasenaIncorrecta();

        assertThat(credencialesGuardadas().failedAttempts()).isEqualTo(1);

        intentarConContrasenaIncorrecta();

        assertThat(credencialesGuardadas().failedAttempts()).isEqualTo(2);
    }

    @Test
    void deberia_bloquear_la_cuenta_al_quinto_intento_fallido_RN_006() {
        for (int i = 0; i < UserCredentials.INTENTOS_MAXIMOS; i++) {
            intentarConContrasenaIncorrecta();
        }

        UserCredentials guardadas = credencialesGuardadas();
        assertThat(guardadas.failedAttempts()).isEqualTo(UserCredentials.INTENTOS_MAXIMOS);
        assertThat(guardadas.estaBloqueada(reloj.instant())).isTrue();

        // Con la contrasena correcta si se dice que esta bloqueada, porque quien la
        // sabe no averigua nada nuevo (criterio 12).
        assertThatThrownBy(this::entrar).isInstanceOf(AccountLockedException.class);
    }

    @Test
    void deberia_limpiar_el_contador_al_entrar_correctamente_RN_006() {
        intentarConContrasenaIncorrecta();
        intentarConContrasenaIncorrecta();

        entrar();

        assertThat(credencialesGuardadas().failedAttempts()).isZero();
        assertThat(credencialesGuardadas().lockedUntil()).isNull();
    }

    @Test
    void deberia_registrar_los_intentos_en_la_auditoria() {
        intentarConContrasenaIncorrecta();
        entrar();

        assertThat(contarIntentos(false)).isEqualTo(1);
        assertThat(contarIntentos(true)).isEqualTo(1);

        // El correo no se guarda en claro (docs/operacion/datos-personales.md).
        long enClaro = jdbc.sql("SELECT count(*) FROM login_attempts WHERE email_hash = :correo")
                .param("correo", usuario.email().value())
                .query(Long.class)
                .single();
        assertThat(enClaro).isZero();
    }

    // El unico sobre token_hash es lo que garantiza que dos sesiones no puedan
    // compartir credencial ni por un fallo de generacion.
    @Test
    void deberia_rechazar_dos_tokens_de_refresco_con_el_mismo_hash() {
        Instant ahora = reloj.instant();
        String hash = "hash-repetido-" + UUID.randomUUID();
        refrescos.guardar(RefreshToken.abrirSesion(usuario.id(), hash, ahora, RefreshToken.VIGENCIA, null, null));

        RefreshToken duplicado = RefreshToken.abrirSesion(usuario.id(), hash, ahora, RefreshToken.VIGENCIA, null, null);

        assertThatThrownBy(() -> refrescos.guardar(duplicado)).isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Criterio 20, contra la base real. Es el caso que ninguna simulacion prueba:
     * la persona tiene sesiones abiertas en varios dispositivos y el cambio de
     * contrasena tiene que alcanzarlas a todas, no solo a la del navegador donde
     * lo pidio. Si sobreviviera una, el token de refresco de quien hubiera
     * averiguado la contrasena seguiria sirviendo 30 dias mas, y el cambio no
     * habria servido de nada.
     */
    @Test
    void deberia_cerrar_las_sesiones_de_todos_los_dispositivos_al_cambiar_la_contrasena_criterio_20() {
        SessionResult enElPortatil = entrar();
        SessionResult enElMovil = entrar();
        SessionResult enLaTableta = entrar();

        int cortadas = refrescos.revocarTodasDe(usuario.id(), reloj.instant());

        assertThat(cortadas).isEqualTo(3);
        for (SessionResult sesion : new SessionResult[] {enElPortatil, enElMovil, enLaTableta}) {
            assertThat(buscar(sesion.refreshToken()).estaRevocado()).isTrue();
            assertThatThrownBy(() -> refresco.execute(new RefreshSessionCommand(sesion.refreshToken(), "Chrome", null)))
                    .isInstanceOf(RefreshTokenInvalidException.class);
        }
    }

    /**
     * Los dos UPDATE de {@code user_credentials} escriben columnas distintas, y
     * confundirlos no se nota con simulaciones.
     *
     * <p>Esta prueba existe porque el fallo ocurrio: el restablecimiento llamaba a
     * {@code actualizar}, que a proposito deja el hash intacto para que un intento
     * fallido no pueda reescribir la credencial. La prueba unitaria pasaba —el
     * simulacro acepta cualquier metodo— y el flujo respondia 204, revocaba las
     * sesiones y consumia el enlace, pero la contrasena vieja seguia sirviendo.
     * Solo la base de datos real puede decir que columna se escribio.
     */
    @Test
    void deberia_escribir_el_hash_solo_al_cambiar_la_contrasena() {
        UserCredentials antes = credenciales.buscarPorUsuario(usuario.id()).orElseThrow();

        // El camino del ingreso: cuenta un fallo y NO toca la credencial.
        credenciales.actualizar(antes.registrarFallo(reloj.instant()));
        assertThat(credenciales.buscarPorUsuario(usuario.id()).orElseThrow().passwordHash())
                .isEqualTo(antes.passwordHash());

        // El camino del restablecimiento: escribe el hash y limpia el contador.
        PasswordHash nuevo = hasher.hashear(new RawPassword("otra-contrasena-larga"));
        credenciales.cambiarContrasena(antes.conNuevaContrasena(nuevo, reloj.instant()));

        UserCredentials despues = credenciales.buscarPorUsuario(usuario.id()).orElseThrow();
        assertThat(despues.passwordHash()).isEqualTo(nuevo).isNotEqualTo(antes.passwordHash());
        assertThat(despues.failedAttempts()).isZero();
        assertThat(despues.estaBloqueada(reloj.instant())).isFalse();
    }

    /**
     * Criterio 23: una cuenta cerrada deja de existir para todo efecto.
     *
     * <p>La fila sigue ahi, anonimizada, pero no se encuentra ni por correo ni por
     * identificador. Es lo que hace que el correo quede libre para volver a
     * registrarse y que un token de acceso todavia vigente no sirva para nada.
     */
    @Test
    void no_deberia_encontrar_una_cuenta_cerrada_criterio_23() {
        usuarios.cerrarYAnonimizar(usuario.id(), reloj.instant());

        assertThat(usuarios.buscarPorId(usuario.id())).isEmpty();
        assertThat(usuarios.buscarPorCorreo(usuario.email())).isEmpty();

        // La fila no se borro: sigue ahi para que lo que manana haya que conservar
        // tenga a que referirse.
        long filas = jdbc.sql("SELECT count(*) FROM users WHERE id = :id")
                .param("id", usuario.id().value())
                .query(Long.class)
                .single();
        assertThat(filas).isEqualTo(1);
    }

    /** El correo queda libre: cerrar no puede significar quedarse sin poder volver. */
    @Test
    void deberia_liberar_el_correo_al_cerrar_la_cuenta_criterio_23() {
        Email suyo = usuario.email();
        usuarios.cerrarYAnonimizar(usuario.id(), reloj.instant());

        User devuelta = User.registrar(
                UserId.nuevo(),
                suyo,
                new DisplayName("Ana Maria"),
                new BirthDate(LocalDate.of(1990, 3, 4)),
                UserLocale.ES,
                LocalDate.now(reloj),
                reloj.instant());

        usuarios.crear(devuelta, hasher.hashear(new RawPassword(CONTRASENA)));

        assertThat(usuarios.buscarPorCorreo(suyo)).isPresent();
    }

    // La contrasena se borra, no se anonimiza: no hace falta para nada.
    @Test
    void deberia_borrar_las_credenciales_al_cerrar_criterio_23() {
        usuarios.cerrarYAnonimizar(usuario.id(), reloj.instant());

        assertThat(credenciales.buscarPorUsuario(usuario.id())).isEmpty();
    }

    // Y no toca las de nadie mas: revocar por usuario no puede ser revocar a todos.
    @Test
    void no_deberia_tocar_las_sesiones_de_otra_persona_criterio_20() {
        SessionResult laSuya = entrar();

        refrescos.revocarTodasDe(UserId.nuevo(), reloj.instant());

        assertThat(buscar(laSuya.refreshToken()).esUtilizable(reloj.instant())).isTrue();
    }

    private RefreshToken buscar(String tokenEnClaro) {
        return refrescos
                .buscarPorHash(generadorDeTokens.hashearRecibido(tokenEnClaro))
                .orElseThrow(() -> new AssertionError("El token deberia existir en la base"));
    }

    private int vivosEnLaFamiliaDe(String tokenEnClaro) {
        return jdbc.sql("""
                        SELECT count(*)
                        FROM refresh_tokens
                        WHERE family_id = :familia AND revoked_at IS NULL AND replaced_by IS NULL
                        """)
                .param("familia", buscar(tokenEnClaro).familyId().value())
                .query(Integer.class)
                .single();
    }

    private UserCredentials credencialesGuardadas() {
        return credenciales
                .buscarPorUsuario(usuario.id())
                .orElseThrow(() -> new AssertionError("Las credenciales deberian existir"));
    }

    /**
     * Se filtra por el hash del correo de esta prueba: el contenedor es uno para toda
     * la clase y la tabla de auditoria acumula los intentos de las demas.
     *
     * <p>El hash se recalcula aqui en lugar de pedirselo al adaptador. Es a proposito:
     * si alguien cambia como se hashea el correo, esta prueba lo nota, y ese cambio
     * hace ilegibles todos los registros anteriores.
     */
    private long contarIntentos(boolean exitosos) {
        return jdbc.sql("SELECT count(*) FROM login_attempts WHERE email_hash = :correo AND succeeded = :exitoso")
                .param("correo", hashDelCorreo())
                .param("exitoso", exitosos)
                .query(Long.class)
                .single();
    }

    private String hashDelCorreo() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(digest.digest(usuario.email().value().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
