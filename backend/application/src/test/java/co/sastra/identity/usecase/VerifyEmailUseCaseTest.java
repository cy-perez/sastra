package co.sastra.identity.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.sastra.identity.dto.VerifyEmailCommand;
import co.sastra.identity.dto.VerifyEmailResult;
import co.sastra.identity.exception.VerificationTokenExpiredException;
import co.sastra.identity.exception.VerificationTokenInvalidException;
import co.sastra.identity.model.BirthDate;
import co.sastra.identity.model.DisplayName;
import co.sastra.identity.model.Email;
import co.sastra.identity.model.TokenPurpose;
import co.sastra.identity.model.User;
import co.sastra.identity.model.UserId;
import co.sastra.identity.model.UserLocale;
import co.sastra.identity.model.VerificationToken;
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
class VerifyEmailUseCaseTest {

    private static final Instant AHORA = Instant.parse("2026-08-17T15:00:00Z");
    private static final Instant EMITIDO = AHORA.minus(Duration.ofHours(1));

    @Mock
    private UserRepository usuarios;

    @Mock
    private VerificationTokenRepository tokens;

    @Mock
    private TokenGenerator generadorDeTokens;

    private VerifyEmailUseCase caso;
    private User usuario;

    @BeforeEach
    void prepararCaso() {
        caso = new VerifyEmailUseCase(usuarios, tokens, generadorDeTokens, Clock.fixed(AHORA, ZoneOffset.UTC));
        usuario = User.registrar(
                UserId.nuevo(),
                new Email("ana@correo.co"),
                new DisplayName("Ana Maria"),
                new BirthDate(LocalDate.of(1990, 3, 4)),
                UserLocale.ES,
                LocalDate.of(2026, 8, 17),
                EMITIDO);
    }

    private VerificationToken tokenDeVerificacion() {
        return VerificationToken.emitir(
                usuario.id(),
                TokenPurpose.EMAIL_VERIFICATION,
                "hash",
                EMITIDO,
                VerificationToken.VIGENCIA_VERIFICACION_DE_CORREO);
    }

    private void conTokenEncontrado(VerificationToken token) {
        when(generadorDeTokens.hashearRecibido("token-claro")).thenReturn("hash");
        when(tokens.buscarPorHash("hash")).thenReturn(Optional.of(token));
    }

    @Test
    void deberia_activar_la_cuenta_con_un_enlace_vigente() {
        conTokenEncontrado(tokenDeVerificacion());
        when(usuarios.buscarPorId(usuario.id())).thenReturn(Optional.of(usuario));

        VerifyEmailResult resultado = caso.execute(new VerifyEmailCommand("token-claro"));

        ArgumentCaptor<User> actualizado = ArgumentCaptor.forClass(User.class);
        verify(usuarios).actualizar(actualizado.capture());
        assertThat(actualizado.getValue().tieneElCorreoVerificado()).isTrue();
        assertThat(actualizado.getValue().emailVerifiedAt()).isEqualTo(AHORA);
        assertThat(resultado.yaEstabaVerificado()).isFalse();
        assertThat(resultado.email()).isEqualTo("ana@correo.co");
    }

    @Test
    void deberia_marcar_el_token_como_usado_para_que_no_sirva_dos_veces_RN_003() {
        conTokenEncontrado(tokenDeVerificacion());
        when(usuarios.buscarPorId(usuario.id())).thenReturn(Optional.of(usuario));

        caso.execute(new VerifyEmailCommand("token-claro"));

        ArgumentCaptor<VerificationToken> usado = ArgumentCaptor.forClass(VerificationToken.class);
        verify(tokens).actualizar(usado.capture());
        assertThat(usado.getValue().yaSeUso()).isTrue();
    }

    // El valor que llega en el enlace nunca se consulta tal cual: se hashea antes.
    @Test
    void deberia_buscar_por_el_hash_y_no_por_el_valor_del_enlace() {
        conTokenEncontrado(tokenDeVerificacion());
        when(usuarios.buscarPorId(usuario.id())).thenReturn(Optional.of(usuario));

        caso.execute(new VerifyEmailCommand("token-claro"));

        verify(tokens).buscarPorHash("hash");
        verify(tokens, never()).buscarPorHash("token-claro");
    }

    @Test
    void deberia_rechazar_un_enlace_que_no_existe() {
        when(generadorDeTokens.hashearRecibido(any())).thenReturn("hash");
        when(tokens.buscarPorHash("hash")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> caso.execute(new VerifyEmailCommand("inventado")))
                .isInstanceOf(VerificationTokenInvalidException.class);
    }

    @Test
    void deberia_rechazar_un_enlace_caducado_RN_003() {
        VerificationToken viejo = VerificationToken.emitir(
                usuario.id(),
                TokenPurpose.EMAIL_VERIFICATION,
                "hash",
                AHORA.minus(Duration.ofHours(25)),
                VerificationToken.VIGENCIA_VERIFICACION_DE_CORREO);
        conTokenEncontrado(viejo);

        assertThatThrownBy(() -> caso.execute(new VerifyEmailCommand("token-claro")))
                .isInstanceOf(VerificationTokenExpiredException.class);

        verify(usuarios, never()).actualizar(any());
    }

    @Test
    void deberia_rechazar_un_enlace_ya_usado_RN_003() {
        conTokenEncontrado(tokenDeVerificacion().marcarUsado(AHORA.minusSeconds(60)));

        assertThatThrownBy(() -> caso.execute(new VerifyEmailCommand("token-claro")))
                .isInstanceOf(VerificationTokenInvalidException.class);
    }

    // Un token de restablecimiento no activa una cuenta: mezclar los propositos
    // convertiria un enlace en otro.
    @Test
    void deberia_rechazar_un_token_de_otro_proposito() {
        VerificationToken deRestablecimiento = VerificationToken.emitir(
                usuario.id(),
                TokenPurpose.PASSWORD_RESET,
                "hash",
                EMITIDO,
                VerificationToken.VIGENCIA_RESTABLECIMIENTO);
        conTokenEncontrado(deRestablecimiento);

        assertThatThrownBy(() -> caso.execute(new VerifyEmailCommand("token-claro")))
                .isInstanceOf(VerificationTokenInvalidException.class);
    }

    @Test
    void deberia_rechazar_un_token_cuyo_usuario_ya_no_existe() {
        conTokenEncontrado(tokenDeVerificacion());
        when(usuarios.buscarPorId(usuario.id())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> caso.execute(new VerifyEmailCommand("token-claro")))
                .isInstanceOf(VerificationTokenInvalidException.class);
    }

    @Test
    void deberia_avisar_cuando_la_cuenta_ya_estaba_verificada() {
        conTokenEncontrado(tokenDeVerificacion());
        when(usuarios.buscarPorId(usuario.id())).thenReturn(Optional.of(usuario.conCorreoVerificado(EMITIDO)));

        VerifyEmailResult resultado = caso.execute(new VerifyEmailCommand("token-claro"));

        assertThat(resultado.yaEstabaVerificado()).isTrue();
    }
}
