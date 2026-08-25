package co.sendik.identity.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.sendik.identity.dto.ForgotPasswordCommand;
import co.sendik.identity.model.BirthDate;
import co.sendik.identity.model.DisplayName;
import co.sendik.identity.model.Email;
import co.sendik.identity.model.TokenPurpose;
import co.sendik.identity.model.User;
import co.sendik.identity.model.UserId;
import co.sendik.identity.model.UserLocale;
import co.sendik.identity.model.VerificationToken;
import co.sendik.identity.port.out.MailSender;
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
class ForgotPasswordUseCaseTest {

    private static final Instant AHORA = Instant.parse("2026-08-17T15:00:00Z");

    @Mock
    private UserRepository usuarios;

    @Mock
    private VerificationTokenRepository tokens;

    @Mock
    private TokenGenerator generadorDeTokens;

    @Mock
    private MailSender correo;

    private ForgotPasswordUseCase caso;
    private User usuario;

    @BeforeEach
    void prepararCaso() {
        caso = new ForgotPasswordUseCase(
                usuarios, tokens, generadorDeTokens, correo, Clock.fixed(AHORA, ZoneOffset.UTC));

        usuario = User.registrar(
                UserId.nuevo(),
                new Email("ana@correo.co"),
                new DisplayName("Ana Maria"),
                new BirthDate(LocalDate.of(1990, 3, 4)),
                UserLocale.ES,
                LocalDate.of(2026, 8, 17),
                AHORA.minus(Duration.ofDays(30)));
    }

    private void conCuenta() {
        when(usuarios.buscarPorCorreo(new Email("ana@correo.co"))).thenReturn(Optional.of(usuario));
        when(generadorDeTokens.generar()).thenReturn(new TokenGenerator.GeneratedToken("en-claro", "el-hash"));
    }

    @Test
    void deberia_emitir_un_token_de_restablecimiento_y_enviarlo() {
        conCuenta();

        caso.execute(new ForgotPasswordCommand("ana@correo.co"));

        ArgumentCaptor<VerificationToken> emitido = ArgumentCaptor.forClass(VerificationToken.class);
        verify(tokens).guardar(emitido.capture());

        assertThat(emitido.getValue().purpose()).isEqualTo(TokenPurpose.PASSWORD_RESET);
        assertThat(emitido.getValue().userId()).isEqualTo(usuario.id());
        verify(correo).enviarRestablecimientoDeContrasena(usuario, "en-claro");
    }

    // Criterio 18: el enlace caduca a los 30 minutos, no a las 24 horas como el
    // de verificacion.
    @Test
    void deberia_dar_treinta_minutos_de_vigencia_criterio_18() {
        conCuenta();

        caso.execute(new ForgotPasswordCommand("ana@correo.co"));

        ArgumentCaptor<VerificationToken> emitido = ArgumentCaptor.forClass(VerificationToken.class);
        verify(tokens).guardar(emitido.capture());

        assertThat(emitido.getValue().expiresAt()).isEqualTo(AHORA.plus(Duration.ofMinutes(30)));
    }

    /**
     * Criterio 19, y es lo que define este caso de uso. Un correo sin cuenta no
     * lanza, no devuelve nada distinto y no deja rastro: si el formulario
     * respondiera de otro modo, serviria para averiguar quien tiene cuenta en
     * Sendik escribiendo direcciones una por una.
     */
    @Test
    void no_deberia_distinguir_un_correo_que_no_existe_criterio_19() {
        when(usuarios.buscarPorCorreo(new Email("nadie@correo.co"))).thenReturn(Optional.empty());

        assertThatCode(() -> caso.execute(new ForgotPasswordCommand("nadie@correo.co")))
                .doesNotThrowAnyException();

        verifyNoInteractions(tokens, correo, generadorDeTokens);
    }

    // El correo se normaliza antes de buscar: quien escribio su direccion con
    // mayusculas tiene que recibir su enlace igual.
    @Test
    void deberia_normalizar_el_correo_antes_de_buscar() {
        conCuenta();

        caso.execute(new ForgotPasswordCommand("  ANA@Correo.CO "));

        verify(usuarios).buscarPorCorreo(new Email("ana@correo.co"));
        verify(correo).enviarRestablecimientoDeContrasena(any(), any());
    }

    // Se guarda el hash y nunca el valor: quien lea la base no puede usar el
    // enlace de nadie.
    @Test
    void deberia_guardar_el_hash_y_mandar_el_valor_en_claro() {
        conCuenta();

        caso.execute(new ForgotPasswordCommand("ana@correo.co"));

        ArgumentCaptor<VerificationToken> emitido = ArgumentCaptor.forClass(VerificationToken.class);
        verify(tokens).guardar(emitido.capture());

        assertThat(emitido.getValue().tokenHash()).isEqualTo("el-hash");
        verify(correo).enviarRestablecimientoDeContrasena(usuario, "en-claro");
    }
}
