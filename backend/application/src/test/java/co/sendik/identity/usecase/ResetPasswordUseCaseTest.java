package co.sendik.identity.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.sendik.identity.dto.ResetPasswordCommand;
import co.sendik.identity.exception.BreachedPasswordException;
import co.sendik.identity.exception.PasswordTooShortException;
import co.sendik.identity.exception.ResetTokenExpiredException;
import co.sendik.identity.exception.ResetTokenInvalidException;
import co.sendik.identity.model.BirthDate;
import co.sendik.identity.model.DisplayName;
import co.sendik.identity.model.Email;
import co.sendik.identity.model.PasswordHash;
import co.sendik.identity.model.RawPassword;
import co.sendik.identity.model.TokenPurpose;
import co.sendik.identity.model.User;
import co.sendik.identity.model.UserCredentials;
import co.sendik.identity.model.UserId;
import co.sendik.identity.model.UserLocale;
import co.sendik.identity.model.VerificationToken;
import co.sendik.identity.port.out.BreachedPasswordChecker;
import co.sendik.identity.port.out.CredentialsRepository;
import co.sendik.identity.port.out.MailSender;
import co.sendik.identity.port.out.PasswordHasher;
import co.sendik.identity.port.out.RefreshTokenRepository;
import co.sendik.identity.port.out.TokenGenerator;
import co.sendik.identity.port.out.UserRepository;
import co.sendik.identity.port.out.VerificationTokenRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResetPasswordUseCaseTest {

    private static final Instant AHORA = Instant.parse("2026-08-17T15:00:00Z");
    private static final String NUEVA = "una-contrasena-larga";

    @Mock
    private UserRepository usuarios;

    @Mock
    private VerificationTokenRepository tokens;

    @Mock
    private CredentialsRepository credenciales;

    @Mock
    private RefreshTokenRepository refrescos;

    @Mock
    private TokenGenerator generadorDeTokens;

    @Mock
    private PasswordHasher hasher;

    @Mock
    private BreachedPasswordChecker filtradas;

    @Mock
    private MailSender correo;

    private ResetPasswordUseCase caso;
    private User usuario;
    private UserCredentials actuales;

    @BeforeEach
    void prepararCaso() {
        caso = new ResetPasswordUseCase(
                usuarios,
                tokens,
                credenciales,
                refrescos,
                generadorDeTokens,
                hasher,
                filtradas,
                correo,
                Clock.fixed(AHORA, ZoneOffset.UTC));

        usuario = User.registrar(
                UserId.nuevo(),
                new Email("ana@correo.co"),
                new DisplayName("Ana Maria"),
                new BirthDate(LocalDate.of(1990, 3, 4)),
                UserLocale.ES,
                LocalDate.of(2026, 8, 17),
                AHORA.minus(Duration.ofDays(30)));

        actuales = UserCredentials.rehidratar(
                usuario.id(), new PasswordHash("hash-viejo"), AHORA.minus(Duration.ofDays(30)), 0, null);
    }

    private VerificationToken tokenVigente() {
        return VerificationToken.emitir(
                usuario.id(),
                TokenPurpose.PASSWORD_RESET,
                "el-hash",
                AHORA.minus(Duration.ofMinutes(5)),
                VerificationToken.VIGENCIA_RESTABLECIMIENTO);
    }

    private void conToken(VerificationToken token) {
        when(generadorDeTokens.hashearRecibido("en-claro")).thenReturn("el-hash");
        when(tokens.buscarPorHash("el-hash")).thenReturn(Optional.of(token));
    }

    private void conTodoListo() {
        conToken(tokenVigente());
        when(filtradas.verificar(any())).thenReturn(BreachedPasswordChecker.Resultado.LIMPIA);
        when(usuarios.buscarPorId(usuario.id())).thenReturn(Optional.of(usuario));
        when(credenciales.buscarPorUsuario(usuario.id())).thenReturn(Optional.of(actuales));
        when(hasher.hashear(new RawPassword(NUEVA))).thenReturn(new PasswordHash("hash-nuevo"));
    }

    private ResetPasswordCommand comando() {
        return new ResetPasswordCommand("en-claro", NUEVA);
    }

    @Test
    void deberia_cambiar_la_contrasena() {
        conTodoListo();

        caso.execute(comando());

        ArgumentCaptor<UserCredentials> guardadas = ArgumentCaptor.forClass(UserCredentials.class);
        verify(credenciales).cambiarContrasena(guardadas.capture());

        assertThat(guardadas.getValue().passwordHash()).isEqualTo(new PasswordHash("hash-nuevo"));
        assertThat(guardadas.getValue().passwordUpdatedAt()).isEqualTo(AHORA);
    }

    // Criterio 18: el enlace sirve una sola vez.
    @Test
    void deberia_consumir_el_token_criterio_18() {
        conTodoListo();

        caso.execute(comando());

        ArgumentCaptor<VerificationToken> usado = ArgumentCaptor.forClass(VerificationToken.class);
        verify(tokens).actualizar(usado.capture());
        assertThat(usado.getValue().yaSeUso()).isTrue();
    }

    /**
     * Criterio 20. No basta con cambiar la contrasena: el token de refresco dura 30
     * dias y no depende de ella, asi que quien tuviera una sesion abierta seguiria
     * dentro un mes despues del cambio.
     */
    @Test
    void deberia_cerrar_todas_las_sesiones_y_avisar_criterio_20() {
        conTodoListo();

        caso.execute(comando());

        verify(refrescos).revocarTodasDe(usuario.id(), AHORA);
        verify(correo).enviarAvisoDeContrasenaCambiada(usuario);
    }

    /**
     * Restablecer levanta el bloqueo de RN-006. Quien llega aqui demostro control
     * del buzon, que es mas fuerte que la contrasena; mantener los quince minutos
     * castigaria a la victima del ataque que los provoco.
     */
    @Test
    void deberia_levantar_el_bloqueo_por_intentos_RN_006() {
        actuales = UserCredentials.rehidratar(
                usuario.id(),
                new PasswordHash("hash-viejo"),
                AHORA.minus(Duration.ofDays(30)),
                5,
                AHORA.plus(Duration.ofMinutes(10)));
        conTodoListo();

        caso.execute(comando());

        ArgumentCaptor<UserCredentials> guardadas = ArgumentCaptor.forClass(UserCredentials.class);
        verify(credenciales).cambiarContrasena(guardadas.capture());

        assertThat(guardadas.getValue().estaBloqueada(AHORA)).isFalse();
        assertThat(guardadas.getValue().failedAttempts()).isZero();
    }

    @Test
    void deberia_rechazar_un_token_que_no_existe() {
        when(generadorDeTokens.hashearRecibido("en-claro")).thenReturn("el-hash");
        when(tokens.buscarPorHash("el-hash")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> caso.execute(comando())).isInstanceOf(ResetTokenInvalidException.class);

        verifyNoInteractions(credenciales, refrescos, correo);
    }

    @Test
    void deberia_rechazar_un_token_ya_usado_criterio_18() {
        conToken(tokenVigente().marcarUsado(AHORA.minus(Duration.ofMinutes(1))));

        assertThatThrownBy(() -> caso.execute(comando())).isInstanceOf(ResetTokenInvalidException.class);

        verifyNoInteractions(credenciales, refrescos, correo);
    }

    @Test
    void deberia_rechazar_un_token_caducado_criterio_18() {
        conToken(VerificationToken.emitir(
                usuario.id(),
                TokenPurpose.PASSWORD_RESET,
                "el-hash",
                AHORA.minus(Duration.ofMinutes(31)),
                VerificationToken.VIGENCIA_RESTABLECIMIENTO));

        assertThatThrownBy(() -> caso.execute(comando())).isInstanceOf(ResetTokenExpiredException.class);

        verifyNoInteractions(credenciales, refrescos, correo);
    }

    /**
     * Sin esta comprobacion, un enlace de verificacion de correo serviria para
     * cambiar la contrasena, y ese dura 24 horas en vez de 30 minutos.
     */
    @Test
    void deberia_rechazar_un_token_de_otro_proposito() {
        conToken(VerificationToken.emitir(
                usuario.id(),
                TokenPurpose.EMAIL_VERIFICATION,
                "el-hash",
                AHORA.minus(Duration.ofMinutes(5)),
                VerificationToken.VIGENCIA_VERIFICACION_DE_CORREO));

        assertThatThrownBy(() -> caso.execute(comando())).isInstanceOf(ResetTokenInvalidException.class);

        verifyNoInteractions(credenciales, refrescos, correo);
    }

    // RN-005 completa: recuperar el acceso no admite una contrasena peor. Si lo
    // hiciera, bastaria pedir el enlace para saltarse la regla.
    @Test
    void deberia_exigir_el_largo_minimo_RN_005() {
        conToken(tokenVigente());

        assertThatThrownBy(() -> caso.execute(new ResetPasswordCommand("en-claro", "corta")))
                .isInstanceOf(PasswordTooShortException.class);

        verifyNoInteractions(credenciales, refrescos, correo);
    }

    @Test
    void deberia_rechazar_una_contrasena_filtrada_RN_005() {
        conToken(tokenVigente());
        when(filtradas.verificar(any())).thenReturn(BreachedPasswordChecker.Resultado.FILTRADA);

        assertThatThrownBy(() -> caso.execute(comando())).isInstanceOf(BreachedPasswordException.class);

        verifyNoInteractions(credenciales, refrescos, correo);
    }

    /**
     * ADR-0013: falla abierto. Dejar a alguien sin poder recuperar su cuenta porque
     * un tercero se cayo convierte la disponibilidad de ese tercero en la nuestra.
     */
    @Test
    void deberia_aceptar_el_cambio_si_no_se_pudo_comprobar_la_lista_ADR_0013() {
        conToken(tokenVigente());
        when(filtradas.verificar(any())).thenReturn(BreachedPasswordChecker.Resultado.NO_SE_PUDO_COMPROBAR);
        when(usuarios.buscarPorId(usuario.id())).thenReturn(Optional.of(usuario));
        when(credenciales.buscarPorUsuario(usuario.id())).thenReturn(Optional.of(actuales));
        when(hasher.hashear(new RawPassword(NUEVA))).thenReturn(new PasswordHash("hash-nuevo"));

        caso.execute(comando());

        verify(credenciales).cambiarContrasena(any());
    }

    /**
     * La contrasena se valida antes de consumir el enlace: quien se equivoca al
     * elegirla puede reintentar sin volver a pedir el correo.
     */
    @Test
    void no_deberia_gastar_el_enlace_si_la_contrasena_no_sirve() {
        conToken(tokenVigente());

        assertThatThrownBy(() -> caso.execute(new ResetPasswordCommand("en-claro", "corta")))
                .isInstanceOf(PasswordTooShortException.class);

        verify(tokens, never()).actualizar(any());
    }

    // La base nunca ve el valor del enlace: se hashea antes de consultar.
    @Test
    void deberia_buscar_por_el_hash_y_no_por_el_valor() {
        conTodoListo();

        caso.execute(comando());

        verify(tokens).buscarPorHash("el-hash");
        verify(tokens, never()).buscarPorHash("en-claro");
    }
}
