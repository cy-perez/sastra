package co.sendik.identity.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.sendik.identity.dto.RequestEmailChangeCommand;
import co.sendik.identity.exception.EmailAlreadyTakenException;
import co.sendik.identity.exception.VerificationTokenExpiredException;
import co.sendik.identity.exception.VerificationTokenInvalidException;
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

/** Cambio de correo con verificacion previa. Criterio 21. */
@ExtendWith(MockitoExtension.class)
class EmailChangeUseCasesTest {

    private static final Instant AHORA = Instant.parse("2026-08-18T15:00:00Z");

    @Mock
    private UserRepository usuarios;

    @Mock
    private VerificationTokenRepository tokens;

    @Mock
    private TokenGenerator generadorDeTokens;

    @Mock
    private MailSender correo;

    private RequestEmailChangeUseCase pedir;
    private ConfirmEmailChangeUseCase confirmar;
    private User usuario;

    @BeforeEach
    void prepararCasos() {
        Clock reloj = Clock.fixed(AHORA, ZoneOffset.UTC);
        pedir = new RequestEmailChangeUseCase(usuarios, tokens, generadorDeTokens, correo, reloj);
        confirmar = new ConfirmEmailChangeUseCase(usuarios, tokens, generadorDeTokens, correo, reloj);

        usuario = User.registrar(
                UserId.nuevo(),
                new Email("ana@correo.co"),
                new DisplayName("Ana Maria"),
                new BirthDate(LocalDate.of(1990, 3, 4)),
                UserLocale.ES,
                LocalDate.of(2026, 8, 18),
                AHORA.minus(Duration.ofDays(30)));
    }

    private void conCuenta() {
        when(usuarios.buscarPorId(usuario.id())).thenReturn(Optional.of(usuario));
    }

    private VerificationToken tokenDeCambio(Email nuevo) {
        return VerificationToken.paraCambioDeCorreo(
                usuario.id(),
                nuevo,
                "el-hash",
                AHORA.minus(Duration.ofMinutes(5)),
                VerificationToken.VIGENCIA_VERIFICACION_DE_CORREO);
    }

    private void conToken(VerificationToken token) {
        when(generadorDeTokens.hashearRecibido("en-claro")).thenReturn("el-hash");
        when(tokens.buscarPorHash("el-hash")).thenReturn(Optional.of(token));
    }

    /**
     * Lo esencial del criterio 21: pedir el cambio no cambia nada. Si reemplazara
     * el correo antes de verificarlo, quien escribiera mal una letra se quedaria
     * fuera de su cuenta sin forma de volver.
     */
    @Test
    void pedir_no_deberia_tocar_el_correo_de_la_cuenta_criterio_21() {
        conCuenta();
        when(usuarios.buscarPorCorreo(new Email("nueva@correo.co"))).thenReturn(Optional.empty());
        when(generadorDeTokens.generar()).thenReturn(new TokenGenerator.GeneratedToken("en-claro", "el-hash"));

        pedir.execute(new RequestEmailChangeCommand(usuario.id(), "nueva@correo.co"));

        verify(usuarios, never()).actualizarCorreo(any());
        verify(correo).enviarConfirmacionDeCorreoNuevo(usuario, new Email("nueva@correo.co"), "en-claro");
    }

    @Test
    void pedir_deberia_guardar_el_correo_pendiente_en_el_token() {
        conCuenta();
        when(usuarios.buscarPorCorreo(new Email("nueva@correo.co"))).thenReturn(Optional.empty());
        when(generadorDeTokens.generar()).thenReturn(new TokenGenerator.GeneratedToken("en-claro", "el-hash"));

        pedir.execute(new RequestEmailChangeCommand(usuario.id(), "nueva@correo.co"));

        ArgumentCaptor<VerificationToken> emitido = ArgumentCaptor.forClass(VerificationToken.class);
        verify(tokens).guardar(emitido.capture());

        assertThat(emitido.getValue().purpose()).isEqualTo(TokenPurpose.EMAIL_CHANGE);
        assertThat(emitido.getValue().newEmail()).isEqualTo(new Email("nueva@correo.co"));
    }

    /**
     * Misma regla que el criterio 2 en el registro: si respondiera distinto,
     * cualquiera con cuenta podria averiguar quien esta registrado probando
     * direcciones desde su perfil.
     */
    @Test
    void pedir_no_deberia_revelar_que_el_correo_ya_tiene_cuenta_criterio_21() {
        conCuenta();
        User ocupante = User.registrar(
                UserId.nuevo(),
                new Email("ocupada@correo.co"),
                new DisplayName("Otra"),
                new BirthDate(LocalDate.of(1990, 3, 4)),
                UserLocale.ES,
                LocalDate.of(2026, 8, 18),
                AHORA);
        when(usuarios.buscarPorCorreo(new Email("ocupada@correo.co"))).thenReturn(Optional.of(ocupante));

        assertThatCode(() -> pedir.execute(new RequestEmailChangeCommand(usuario.id(), "ocupada@correo.co")))
                .doesNotThrowAnyException();

        // Ni token ni enlace: al que lo pidio no se le dice nada.
        verifyNoInteractions(tokens, generadorDeTokens);
        verify(correo, never()).enviarConfirmacionDeCorreoNuevo(any(), any(), any());
        // Pero al titular de esa direccion si se le avisa: es su correo.
        verify(correo).enviarAvisoDeIntentoDeCambioAEsteCorreo(ocupante);
    }

    // Pedir el cambio al correo que ya se tiene no es un error, pero no hay nada
    // que hacer: se termina en silencio, como el caso ocupado.
    @Test
    void pedir_no_deberia_hacer_nada_si_es_el_mismo_correo() {
        conCuenta();

        pedir.execute(new RequestEmailChangeCommand(usuario.id(), "ana@correo.co"));

        verifyNoInteractions(tokens, generadorDeTokens, correo);
    }

    @Test
    void confirmar_deberia_reemplazar_el_correo_y_dejarlo_verificado_criterio_21() {
        conToken(tokenDeCambio(new Email("nueva@correo.co")));
        when(usuarios.buscarPorId(usuario.id())).thenReturn(Optional.of(usuario));
        when(usuarios.buscarPorCorreo(new Email("nueva@correo.co"))).thenReturn(Optional.empty());

        confirmar.execute("en-claro");

        ArgumentCaptor<User> guardado = ArgumentCaptor.forClass(User.class);
        verify(usuarios).actualizarCorreo(guardado.capture());

        assertThat(guardado.getValue().email()).isEqualTo(new Email("nueva@correo.co"));
        // Queda verificado: acaba de demostrar que ese buzon es suyo.
        assertThat(guardado.getValue().tieneElCorreoVerificado()).isTrue();
    }

    /**
     * El aviso va al correo ANTERIOR. Es lo que evita el peor caso: quien robe una
     * sesion cambia el correo y saca al titular de su cuenta en silencio.
     */
    @Test
    void confirmar_deberia_avisar_al_correo_anterior_criterio_21() {
        conToken(tokenDeCambio(new Email("nueva@correo.co")));
        when(usuarios.buscarPorId(usuario.id())).thenReturn(Optional.of(usuario));
        when(usuarios.buscarPorCorreo(new Email("nueva@correo.co"))).thenReturn(Optional.empty());

        confirmar.execute("en-claro");

        verify(correo).enviarAvisoDeCorreoCambiado(usuario, new Email("ana@correo.co"));
    }

    /**
     * RN-001 se comprueba otra vez al confirmar: entre pedir y confirmar puede
     * pasar un dia, y en ese hueco alguien pudo registrarse con esa direccion.
     */
    @Test
    void confirmar_deberia_rechazar_si_alguien_registro_ese_correo_mientras_tanto_RN_001() {
        conToken(tokenDeCambio(new Email("nueva@correo.co")));
        when(usuarios.buscarPorId(usuario.id())).thenReturn(Optional.of(usuario));
        when(usuarios.buscarPorCorreo(new Email("nueva@correo.co")))
                .thenReturn(Optional.of(User.registrar(
                        UserId.nuevo(),
                        new Email("nueva@correo.co"),
                        new DisplayName("Quien llego antes"),
                        new BirthDate(LocalDate.of(1990, 3, 4)),
                        UserLocale.ES,
                        LocalDate.of(2026, 8, 18),
                        AHORA)));

        assertThatThrownBy(() -> confirmar.execute("en-claro")).isInstanceOf(EmailAlreadyTakenException.class);

        verify(usuarios, never()).actualizarCorreo(any());
        verify(tokens, never()).actualizar(any());
    }

    @Test
    void confirmar_deberia_consumir_el_enlace() {
        conToken(tokenDeCambio(new Email("nueva@correo.co")));
        when(usuarios.buscarPorId(usuario.id())).thenReturn(Optional.of(usuario));
        when(usuarios.buscarPorCorreo(new Email("nueva@correo.co"))).thenReturn(Optional.empty());

        confirmar.execute("en-claro");

        ArgumentCaptor<VerificationToken> usado = ArgumentCaptor.forClass(VerificationToken.class);
        verify(tokens).actualizar(usado.capture());
        assertThat(usado.getValue().yaSeUso()).isTrue();
    }

    @Test
    void confirmar_deberia_rechazar_un_enlace_ya_usado() {
        conToken(tokenDeCambio(new Email("nueva@correo.co")).marcarUsado(AHORA.minusSeconds(60)));

        assertThatThrownBy(() -> confirmar.execute("en-claro")).isInstanceOf(VerificationTokenInvalidException.class);

        verify(usuarios, never()).actualizarCorreo(any());
    }

    @Test
    void confirmar_deberia_rechazar_un_enlace_caducado() {
        conToken(VerificationToken.paraCambioDeCorreo(
                usuario.id(),
                new Email("nueva@correo.co"),
                "el-hash",
                AHORA.minus(Duration.ofHours(25)),
                VerificationToken.VIGENCIA_VERIFICACION_DE_CORREO));

        assertThatThrownBy(() -> confirmar.execute("en-claro")).isInstanceOf(VerificationTokenExpiredException.class);
    }

    /**
     * Sin esta comprobacion, un enlace de verificacion de correo o de
     * restablecimiento serviria para cambiar la direccion de la cuenta.
     */
    @Test
    void confirmar_deberia_rechazar_un_token_de_otro_proposito() {
        conToken(VerificationToken.emitir(
                usuario.id(),
                TokenPurpose.EMAIL_VERIFICATION,
                "el-hash",
                AHORA.minus(Duration.ofMinutes(5)),
                VerificationToken.VIGENCIA_VERIFICACION_DE_CORREO));

        assertThatThrownBy(() -> confirmar.execute("en-claro")).isInstanceOf(VerificationTokenInvalidException.class);
    }
}
