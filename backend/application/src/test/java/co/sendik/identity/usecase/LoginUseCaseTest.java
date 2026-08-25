package co.sendik.identity.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.sendik.identity.dto.AuthenticatedUser;
import co.sendik.identity.dto.IssueSessionCommand;
import co.sendik.identity.dto.LoginCommand;
import co.sendik.identity.dto.SessionResult;
import co.sendik.identity.exception.AccountLockedException;
import co.sendik.identity.exception.InvalidCredentialsException;
import co.sendik.identity.model.BirthDate;
import co.sendik.identity.model.DisplayName;
import co.sendik.identity.model.Email;
import co.sendik.identity.model.PasswordHash;
import co.sendik.identity.model.RawPassword;
import co.sendik.identity.model.Role;
import co.sendik.identity.model.User;
import co.sendik.identity.model.UserCredentials;
import co.sendik.identity.model.UserId;
import co.sendik.identity.model.UserLocale;
import co.sendik.identity.port.out.CredentialsRepository;
import co.sendik.identity.port.out.LoginAttemptRecorder;
import co.sendik.identity.port.out.MailSender;
import co.sendik.identity.port.out.PasswordHasher;
import co.sendik.identity.port.out.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoginUseCaseTest {

    private static final Instant AHORA = Instant.parse("2026-08-17T15:00:00Z");
    private static final PasswordHash HASH = new PasswordHash("$argon2id$guardado");

    @Mock
    private UserRepository usuarios;

    @Mock
    private CredentialsRepository credenciales;

    @Mock
    private PasswordHasher hasher;

    @Mock
    private LoginAttemptRecorder intentos;

    @Mock
    private MailSender correo;

    @Mock
    private IssueSessionUseCase abrirSesion;

    private LoginUseCase caso;
    private User usuario;

    @BeforeEach
    void prepararCaso() {
        caso = new LoginUseCase(
                usuarios, credenciales, hasher, intentos, correo, abrirSesion, Clock.fixed(AHORA, ZoneOffset.UTC));

        usuario = User.registrar(
                UserId.nuevo(),
                new Email("ana@correo.co"),
                new DisplayName("Ana Maria"),
                new BirthDate(LocalDate.of(1990, 3, 4)),
                UserLocale.ES,
                LocalDate.of(2026, 8, 17),
                AHORA.minus(Duration.ofDays(10)));
    }

    private LoginCommand comando() {
        return new LoginCommand("ana@correo.co", "contrasena-larga", "Firefox", "ip-hash");
    }

    private UserCredentials credencialesLimpias() {
        return UserCredentials.rehidratar(usuario.id(), HASH, AHORA.minus(Duration.ofDays(10)), 0, null);
    }

    private UserCredentials credencialesBloqueadas() {
        return UserCredentials.rehidratar(
                usuario.id(),
                HASH,
                AHORA.minus(Duration.ofDays(10)),
                UserCredentials.INTENTOS_MAXIMOS,
                AHORA.plus(Duration.ofMinutes(10)));
    }

    private void conCuentaYCredenciales(UserCredentials guardadas) {
        when(usuarios.buscarPorCorreo(new Email("ana@correo.co"))).thenReturn(Optional.of(usuario));
        when(credenciales.buscarPorUsuario(usuario.id())).thenReturn(Optional.of(guardadas));
    }

    private void conContrasenaCorrecta() {
        when(hasher.coincide(any(RawPassword.class), eq(HASH))).thenReturn(true);
    }

    private void conSesionEmitida() {
        when(abrirSesion.execute(any()))
                .thenReturn(new SessionResult(
                        "token-de-acceso",
                        AHORA.plus(Duration.ofMinutes(15)),
                        "token-de-refresco",
                        AHORA.plus(Duration.ofDays(30)),
                        new AuthenticatedUser(usuario.id(), "ana@correo.co", "Ana Maria", false, Set.of(Role.BUYER))));
    }

    // Criterio 10: credenciales correctas devuelven la sesion.
    @Test
    void deberia_abrir_sesion_con_credenciales_correctas() {
        conCuentaYCredenciales(credencialesLimpias());
        conContrasenaCorrecta();
        conSesionEmitida();

        SessionResult sesion = caso.execute(comando());

        assertThat(sesion.accessToken()).isEqualTo("token-de-acceso");
        assertThat(sesion.refreshToken()).isEqualTo("token-de-refresco");
    }

    @Test
    void deberia_pasar_el_navegador_y_la_ip_a_la_sesion_nueva() {
        conCuentaYCredenciales(credencialesLimpias());
        conContrasenaCorrecta();
        conSesionEmitida();

        caso.execute(comando());

        ArgumentCaptor<IssueSessionCommand> emitido = ArgumentCaptor.forClass(IssueSessionCommand.class);
        verify(abrirSesion).execute(emitido.capture());
        assertThat(emitido.getValue().usuario()).isEqualTo(usuario);
        assertThat(emitido.getValue().userAgent()).isEqualTo("Firefox");
        assertThat(emitido.getValue().ipHash()).isEqualTo("ip-hash");
    }

    @Test
    void deberia_limpiar_el_contador_de_intentos_al_entrar_RN_006() {
        conCuentaYCredenciales(UserCredentials.rehidratar(usuario.id(), HASH, AHORA, 3, null));
        conContrasenaCorrecta();
        conSesionEmitida();

        caso.execute(comando());

        ArgumentCaptor<UserCredentials> guardadas = ArgumentCaptor.forClass(UserCredentials.class);
        verify(credenciales).actualizar(guardadas.capture());
        assertThat(guardadas.getValue().failedAttempts()).isZero();
    }

    @Test
    void deberia_registrar_el_intento_exitoso_en_la_auditoria() {
        conCuentaYCredenciales(credencialesLimpias());
        conContrasenaCorrecta();
        conSesionEmitida();

        caso.execute(comando());

        verify(intentos).registrar(new Email("ana@correo.co"), "ip-hash", true, AHORA);
    }

    // Criterio 13: entra igual, y el resumen dice que el correo sigue sin verificar.
    @Test
    void deberia_dejar_entrar_una_cuenta_sin_verificar() {
        conCuentaYCredenciales(credencialesLimpias());
        conContrasenaCorrecta();
        conSesionEmitida();

        SessionResult sesion = caso.execute(comando());

        assertThat(usuario.tieneElCorreoVerificado()).isFalse();
        assertThat(sesion.user().emailVerified()).isFalse();
    }

    @Test
    void deberia_rechazar_una_contrasena_incorrecta_RN_006() {
        conCuentaYCredenciales(credencialesLimpias());
        when(hasher.coincide(any(RawPassword.class), eq(HASH))).thenReturn(false);

        assertThatThrownBy(() -> caso.execute(comando())).isInstanceOf(InvalidCredentialsException.class);

        verify(abrirSesion, never()).execute(any());
        verify(intentos).registrar(new Email("ana@correo.co"), "ip-hash", false, AHORA);
    }

    @Test
    void deberia_sumar_el_intento_fallido_RN_006() {
        conCuentaYCredenciales(credencialesLimpias());
        when(hasher.coincide(any(RawPassword.class), eq(HASH))).thenReturn(false);

        assertThatThrownBy(() -> caso.execute(comando())).isInstanceOf(InvalidCredentialsException.class);

        ArgumentCaptor<UserCredentials> guardadas = ArgumentCaptor.forClass(UserCredentials.class);
        verify(credenciales).actualizar(guardadas.capture());
        assertThat(guardadas.getValue().failedAttempts()).isEqualTo(1);
    }

    // Criterio 11: un correo sin cuenta responde exactamente igual que una
    // contrasena equivocada.
    @Test
    void deberia_responder_igual_cuando_el_correo_no_existe() {
        when(usuarios.buscarPorCorreo(new Email("ana@correo.co"))).thenReturn(Optional.empty());
        when(hasher.coincide(any(RawPassword.class), isNull())).thenReturn(false);

        assertThatThrownBy(() -> caso.execute(comando())).isInstanceOf(InvalidCredentialsException.class);
    }

    // Y tarda lo mismo: se compara contra el hash nulo, que el adaptador compensa.
    // Sin esta llamada, medir el tiempo de respuesta delataria quien tiene cuenta.
    @Test
    void deberia_comparar_la_contrasena_aunque_el_correo_no_exista_criterio_11() {
        when(usuarios.buscarPorCorreo(new Email("ana@correo.co"))).thenReturn(Optional.empty());
        when(hasher.coincide(any(RawPassword.class), isNull())).thenReturn(false);

        assertThatThrownBy(() -> caso.execute(comando())).isInstanceOf(InvalidCredentialsException.class);

        verify(hasher).coincide(any(RawPassword.class), isNull());
        verify(intentos).registrar(new Email("ana@correo.co"), "ip-hash", false, AHORA);
        verifyNoInteractions(correo);
        verify(credenciales, never()).actualizar(any());
    }

    // Criterio 12: el quinto intento bloquea y se avisa al titular.
    @Test
    void deberia_avisar_al_titular_cuando_el_intento_bloquea_la_cuenta_RN_006() {
        conCuentaYCredenciales(
                UserCredentials.rehidratar(usuario.id(), HASH, AHORA, UserCredentials.INTENTOS_MAXIMOS - 1, null));
        when(hasher.coincide(any(RawPassword.class), eq(HASH))).thenReturn(false);

        assertThatThrownBy(() -> caso.execute(comando())).isInstanceOf(InvalidCredentialsException.class);

        verify(correo).enviarAvisoDeCuentaBloqueada(usuario, AHORA.plus(UserCredentials.BLOQUEO));
    }

    @Test
    void no_deberia_avisar_dos_veces_por_el_mismo_bloqueo_RN_006() {
        conCuentaYCredenciales(credencialesBloqueadas());
        when(hasher.coincide(any(RawPassword.class), eq(HASH))).thenReturn(false);

        assertThatThrownBy(() -> caso.execute(comando())).isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(correo);
    }

    @Test
    void no_deberia_avisar_antes_del_quinto_intento_RN_006() {
        conCuentaYCredenciales(credencialesLimpias());
        when(hasher.coincide(any(RawPassword.class), eq(HASH))).thenReturn(false);

        assertThatThrownBy(() -> caso.execute(comando())).isInstanceOf(InvalidCredentialsException.class);

        verifyNoInteractions(correo);
    }

    // Con la contrasena correcta si se dice que esta bloqueada: quien la sabe no
    // averigua nada nuevo, y necesita entender por que no entra.
    @Test
    void deberia_informar_del_bloqueo_cuando_la_contrasena_era_correcta_RN_006() {
        UserCredentials bloqueadas = credencialesBloqueadas();
        conCuentaYCredenciales(bloqueadas);
        conContrasenaCorrecta();

        assertThatExceptionOfType(AccountLockedException.class)
                .isThrownBy(() -> caso.execute(comando()))
                .satisfies(fallo -> assertThat(fallo.desbloqueoEn()).isEqualTo(bloqueadas.lockedUntil()));

        verify(abrirSesion, never()).execute(any());
        verify(intentos).registrar(new Email("ana@correo.co"), "ip-hash", false, AHORA);
    }

    // Y con la contrasena incorrecta no: decirle "esta bloqueada" a quien no la
    // sabe le confirma que la cuenta existe y que alguien la esta intentando.
    @Test
    void no_deberia_revelar_el_bloqueo_a_quien_no_sabe_la_contrasena_criterio_11() {
        conCuentaYCredenciales(credencialesBloqueadas());
        when(hasher.coincide(any(RawPassword.class), eq(HASH))).thenReturn(false);

        assertThatThrownBy(() -> caso.execute(comando()))
                .isInstanceOf(InvalidCredentialsException.class)
                .isNotInstanceOf(AccountLockedException.class);
    }

    @Test
    void deberia_dejar_entrar_cuando_el_bloqueo_ya_vencio_RN_006() {
        conCuentaYCredenciales(UserCredentials.rehidratar(
                usuario.id(),
                HASH,
                AHORA.minus(Duration.ofDays(10)),
                UserCredentials.INTENTOS_MAXIMOS,
                AHORA.minusSeconds(1)));
        conContrasenaCorrecta();
        conSesionEmitida();

        SessionResult sesion = caso.execute(comando());

        assertThat(sesion.accessToken()).isEqualTo("token-de-acceso");
    }

    // Una cuenta sin credenciales guardadas no deberia existir, pero si existiera
    // no debe dejar entrar a nadie: es exactamente el caso en que un `orElse(true)`
    // mal puesto abre la puerta sin contrasena.
    @Test
    void deberia_rechazar_una_cuenta_sin_credenciales_guardadas() {
        when(usuarios.buscarPorCorreo(new Email("ana@correo.co"))).thenReturn(Optional.of(usuario));
        when(credenciales.buscarPorUsuario(usuario.id())).thenReturn(Optional.empty());
        when(hasher.coincide(any(RawPassword.class), isNull())).thenReturn(false);

        assertThatThrownBy(() -> caso.execute(comando())).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void deberia_rechazar_un_correo_con_formato_invalido() {
        assertThatThrownBy(() -> caso.execute(new LoginCommand("no-es-correo", "contrasena-larga", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
