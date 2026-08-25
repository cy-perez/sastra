package co.sendik.identity.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.sendik.identity.dto.RequestEmailVerificationCommand;
import co.sendik.identity.exception.ResendLimitReachedException;
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
class RequestEmailVerificationUseCaseTest {

    private static final Instant AHORA = Instant.parse("2026-08-17T15:00:00Z");

    @Mock
    private UserRepository usuarios;

    @Mock
    private VerificationTokenRepository tokens;

    @Mock
    private TokenGenerator generadorDeTokens;

    @Mock
    private MailSender correo;

    private RequestEmailVerificationUseCase caso;
    private User usuario;

    @BeforeEach
    void prepararCaso() {
        caso = new RequestEmailVerificationUseCase(
                usuarios, tokens, generadorDeTokens, correo, Clock.fixed(AHORA, ZoneOffset.UTC));

        usuario = User.registrar(
                UserId.nuevo(),
                new Email("ana@correo.co"),
                new DisplayName("Ana Maria"),
                new BirthDate(LocalDate.of(1990, 3, 4)),
                UserLocale.ES,
                LocalDate.of(2026, 8, 17),
                AHORA.minus(Duration.ofDays(1)));
    }

    private RequestEmailVerificationCommand comando() {
        return new RequestEmailVerificationCommand(usuario.id());
    }

    private void conUsuario(User cual) {
        when(usuarios.buscarPorId(usuario.id())).thenReturn(Optional.of(cual));
    }

    private void sinReenviosPrevios() {
        when(tokens.contarEmitidosDesde(eq(usuario.id()), eq(TokenPurpose.EMAIL_VERIFICATION), any()))
                .thenReturn(0);
    }

    // Criterio 13: la persona entra sin verificar y pide otro enlace desde el aviso.
    @Test
    void deberia_emitir_un_enlace_nuevo_criterio_13() {
        conUsuario(usuario);
        sinReenviosPrevios();
        when(generadorDeTokens.generar())
                .thenReturn(new TokenGenerator.GeneratedToken("token-en-claro", "hash-del-token"));

        caso.execute(comando());

        ArgumentCaptor<VerificationToken> emitido = ArgumentCaptor.forClass(VerificationToken.class);
        verify(tokens).guardar(emitido.capture());
        assertThat(emitido.getValue().purpose()).isEqualTo(TokenPurpose.EMAIL_VERIFICATION);
        assertThat(emitido.getValue().tokenHash()).isEqualTo("hash-del-token");
        assertThat(emitido.getValue().expiresAt()).isEqualTo(AHORA.plus(Duration.ofHours(24)));
        verify(correo).enviarVerificacionDeCorreo(usuario, "token-en-claro");
    }

    // El limite se comparte con el reenvio por enlace y se cuenta sobre los tokens
    // emitidos: si contara por endpoint, alternar entre los dos daria seis por hora.
    @Test
    void deberia_respetar_el_limite_de_tres_por_hora_criterio_8() {
        conUsuario(usuario);
        when(tokens.contarEmitidosDesde(eq(usuario.id()), eq(TokenPurpose.EMAIL_VERIFICATION), any()))
                .thenReturn(ResendVerificationUseCase.MAXIMO_REENVIOS_POR_HORA);

        assertThatThrownBy(() -> caso.execute(comando())).isInstanceOf(ResendLimitReachedException.class);

        verify(tokens, never()).guardar(any());
        verifyNoInteractions(correo);
    }

    @Test
    void deberia_contar_los_reenvios_de_la_ultima_hora() {
        conUsuario(usuario);
        sinReenviosPrevios();
        when(generadorDeTokens.generar())
                .thenReturn(new TokenGenerator.GeneratedToken("token-en-claro", "hash-del-token"));

        caso.execute(comando());

        verify(tokens)
                .contarEmitidosDesde(usuario.id(), TokenPurpose.EMAIL_VERIFICATION, AHORA.minus(Duration.ofHours(1)));
    }

    // Pedir un enlace con el correo ya verificado no es un error: es una pantalla
    // que se quedo abierta. No se emite nada y no se falla.
    @Test
    void no_deberia_emitir_nada_si_el_correo_ya_esta_verificado() {
        conUsuario(usuario.conCorreoVerificado(AHORA.minus(Duration.ofHours(2))));

        caso.execute(comando());

        verify(tokens, never()).guardar(any());
        verifyNoInteractions(correo, generadorDeTokens);
    }

    @Test
    void no_deberia_fallar_si_la_cuenta_ya_no_existe() {
        when(usuarios.buscarPorId(usuario.id())).thenReturn(Optional.empty());

        caso.execute(comando());

        verifyNoInteractions(tokens, correo, generadorDeTokens);
    }
}
