package co.sastra.identity.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.sastra.identity.dto.ResendVerificationCommand;
import co.sastra.identity.exception.ResendLimitReachedException;
import co.sastra.identity.exception.VerificationTokenInvalidException;
import co.sastra.identity.model.BirthDate;
import co.sastra.identity.model.DisplayName;
import co.sastra.identity.model.Email;
import co.sastra.identity.model.TokenPurpose;
import co.sastra.identity.model.User;
import co.sastra.identity.model.UserId;
import co.sastra.identity.model.UserLocale;
import co.sastra.identity.model.VerificationToken;
import co.sastra.identity.port.out.MailSender;
import co.sastra.identity.port.out.TokenGenerator;
import co.sastra.identity.port.out.UserRepository;
import co.sastra.identity.port.out.VerificationTokenRepository;
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
class ResendVerificationUseCaseTest {

    private static final Instant AHORA = Instant.parse("2026-08-17T15:00:00Z");
    private static final Instant EMITIDO = AHORA.minus(Duration.ofHours(30));

    @Mock
    private UserRepository usuarios;

    @Mock
    private VerificationTokenRepository tokens;

    @Mock
    private TokenGenerator generadorDeTokens;

    @Mock
    private MailSender correo;

    private ResendVerificationUseCase caso;
    private User usuario;

    @BeforeEach
    void prepararCaso() {
        caso = new ResendVerificationUseCase(
                usuarios, tokens, generadorDeTokens, correo, Clock.fixed(AHORA, ZoneOffset.UTC));
        usuario = User.registrar(
                UserId.nuevo(),
                new Email("ana@correo.co"),
                new DisplayName("Ana Maria"),
                new BirthDate(LocalDate.of(1990, 3, 4)),
                UserLocale.ES,
                LocalDate.of(2026, 8, 17),
                EMITIDO);
    }

    private void conTokenCaducadoEncontrado() {
        when(generadorDeTokens.hashearRecibido("caducado")).thenReturn("hash");
        when(tokens.buscarPorHash("hash"))
                .thenReturn(Optional.of(VerificationToken.emitir(
                        usuario.id(),
                        TokenPurpose.EMAIL_VERIFICATION,
                        "hash",
                        EMITIDO,
                        VerificationToken.VIGENCIA_VERIFICACION_DE_CORREO)));
        when(usuarios.buscarPorId(usuario.id())).thenReturn(Optional.of(usuario));
    }

    @Test
    void deberia_emitir_un_token_nuevo_y_reenviar_el_correo() {
        conTokenCaducadoEncontrado();
        when(tokens.contarEmitidosDesde(any(), any(), any())).thenReturn(0);
        when(generadorDeTokens.generar()).thenReturn(new TokenGenerator.GeneratedToken("nuevo-claro", "nuevo-hash"));

        caso.execute(new ResendVerificationCommand("caducado"));

        ArgumentCaptor<VerificationToken> emitido = ArgumentCaptor.forClass(VerificationToken.class);
        verify(tokens).guardar(emitido.capture());
        assertThat(emitido.getValue().tokenHash()).isEqualTo("nuevo-hash");
        assertThat(emitido.getValue().expiresAt()).isEqualTo(AHORA.plus(Duration.ofHours(24)));
        verify(correo).enviarVerificacionDeCorreo(eq(usuario), eq("nuevo-claro"));
    }

    @Test
    void deberia_contar_los_reenvios_dentro_de_la_ultima_hora() {
        conTokenCaducadoEncontrado();
        when(tokens.contarEmitidosDesde(any(), any(), any())).thenReturn(0);
        when(generadorDeTokens.generar()).thenReturn(new TokenGenerator.GeneratedToken("nuevo-claro", "nuevo-hash"));

        caso.execute(new ResendVerificationCommand("caducado"));

        verify(tokens)
                .contarEmitidosDesde(usuario.id(), TokenPurpose.EMAIL_VERIFICATION, AHORA.minus(Duration.ofHours(1)));
    }

    @Test
    void deberia_permitir_el_tercer_reenvio_de_la_hora() {
        conTokenCaducadoEncontrado();
        when(tokens.contarEmitidosDesde(any(), any(), any())).thenReturn(2);
        when(generadorDeTokens.generar()).thenReturn(new TokenGenerator.GeneratedToken("nuevo-claro", "nuevo-hash"));

        caso.execute(new ResendVerificationCommand("caducado"));

        verify(correo).enviarVerificacionDeCorreo(any(), any());
    }

    @Test
    void deberia_rechazar_el_cuarto_reenvio_de_la_hora() {
        conTokenCaducadoEncontrado();
        when(tokens.contarEmitidosDesde(any(), any(), any()))
                .thenReturn(ResendVerificationUseCase.MAXIMO_REENVIOS_POR_HORA);

        assertThatThrownBy(() -> caso.execute(new ResendVerificationCommand("caducado")))
                .isInstanceOf(ResendLimitReachedException.class);

        verify(tokens, never()).guardar(any());
        verify(correo, never()).enviarVerificacionDeCorreo(any(), any());
    }

    @Test
    void deberia_rechazar_un_token_que_no_existe() {
        when(generadorDeTokens.hashearRecibido(any())).thenReturn("hash");
        when(tokens.buscarPorHash("hash")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> caso.execute(new ResendVerificationCommand("inventado")))
                .isInstanceOf(VerificationTokenInvalidException.class);
    }

    // Reenviar sobre una cuenta ya activa no tiene sentido, y responder distinto
    // convertiria este endpoint en una forma de averiguar el estado ajeno.
    @Test
    void deberia_rechazar_el_reenvio_si_la_cuenta_ya_esta_verificada() {
        when(generadorDeTokens.hashearRecibido("caducado")).thenReturn("hash");
        when(tokens.buscarPorHash("hash"))
                .thenReturn(Optional.of(VerificationToken.emitir(
                        usuario.id(),
                        TokenPurpose.EMAIL_VERIFICATION,
                        "hash",
                        EMITIDO,
                        VerificationToken.VIGENCIA_VERIFICACION_DE_CORREO)));
        when(usuarios.buscarPorId(usuario.id())).thenReturn(Optional.of(usuario.conCorreoVerificado(AHORA)));

        assertThatThrownBy(() -> caso.execute(new ResendVerificationCommand("caducado")))
                .isInstanceOf(VerificationTokenInvalidException.class);
    }

    @Test
    void deberia_rechazar_un_token_de_otro_proposito() {
        when(generadorDeTokens.hashearRecibido("caducado")).thenReturn("hash");
        when(tokens.buscarPorHash("hash"))
                .thenReturn(Optional.of(VerificationToken.emitir(
                        usuario.id(),
                        TokenPurpose.PASSWORD_RESET,
                        "hash",
                        EMITIDO,
                        VerificationToken.VIGENCIA_RESTABLECIMIENTO)));

        assertThatThrownBy(() -> caso.execute(new ResendVerificationCommand("caducado")))
                .isInstanceOf(VerificationTokenInvalidException.class);
    }
}
