package co.sendik.identity.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.sendik.identity.dto.RegisterUserCommand;
import co.sendik.identity.exception.BreachedPasswordException;
import co.sendik.identity.exception.ConsentRequiredException;
import co.sendik.identity.exception.PasswordTooShortException;
import co.sendik.identity.exception.UnderageException;
import co.sendik.identity.model.Consent;
import co.sendik.identity.model.ConsentDocument;
import co.sendik.identity.model.Email;
import co.sendik.identity.model.PasswordHash;
import co.sendik.identity.model.RawPassword;
import co.sendik.identity.model.User;
import co.sendik.identity.model.UserId;
import co.sendik.identity.model.UserLocale;
import co.sendik.identity.model.VerificationToken;
import co.sendik.identity.port.out.BreachedPasswordChecker;
import co.sendik.identity.port.out.ConsentRepository;
import co.sendik.identity.port.out.LegalDocuments;
import co.sendik.identity.port.out.MailSender;
import co.sendik.identity.port.out.PasswordHasher;
import co.sendik.identity.port.out.TokenGenerator;
import co.sendik.identity.port.out.UserRepository;
import co.sendik.identity.port.out.VerificationTokenRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegisterUserUseCaseTest {

    private static final Instant AHORA = Instant.parse("2026-08-17T15:00:00Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);

    @Mock
    private UserRepository usuarios;

    @Mock
    private ConsentRepository consentimientos;

    @Mock
    private VerificationTokenRepository tokens;

    @Mock
    private PasswordHasher hasher;

    @Mock
    private BreachedPasswordChecker filtradas;

    @Mock
    private TokenGenerator generadorDeTokens;

    @Mock
    private MailSender correo;

    @Mock
    private LegalDocuments documentosLegales;

    private RegisterUserUseCase caso;

    @BeforeEach
    void prepararCaso() {
        caso = new RegisterUserUseCase(
                usuarios,
                consentimientos,
                tokens,
                hasher,
                filtradas,
                generadorDeTokens,
                correo,
                documentosLegales,
                RELOJ);
    }

    private static RegisterUserCommand comandoValido() {
        return new RegisterUserCommand(
                "Ana@Correo.co",
                "una contrasena larga",
                "Ana Maria",
                LocalDate.of(1990, 3, 4),
                "es",
                true,
                true,
                "hash-ip");
    }

    private void conCaminoFeliz() {
        when(filtradas.verificar(any())).thenReturn(BreachedPasswordChecker.Resultado.LIMPIA);
        when(hasher.hashear(any())).thenReturn(new PasswordHash("$argon2id$hash"));
        when(usuarios.buscarPorCorreo(any())).thenReturn(Optional.empty());
        when(generadorDeTokens.generar()).thenReturn(new TokenGenerator.GeneratedToken("token-claro", "token-hash"));
        when(documentosLegales.versionVigente(any())).thenReturn("2026-08-01");
    }

    private static User usuarioExistente() {
        return User.registrar(
                UserId.nuevo(),
                new Email("ana@correo.co"),
                new co.sendik.identity.model.DisplayName("Ana Maria"),
                new co.sendik.identity.model.BirthDate(LocalDate.of(1990, 3, 4)),
                UserLocale.ES,
                LocalDate.of(2026, 8, 17),
                AHORA);
    }

    @Test
    void deberia_crear_la_cuenta_y_enviar_el_correo_de_verificacion() {
        conCaminoFeliz();

        caso.execute(comandoValido());

        ArgumentCaptor<User> creado = ArgumentCaptor.forClass(User.class);
        verify(usuarios).crear(creado.capture(), any());
        assertThat(creado.getValue().email()).isEqualTo(new Email("ana@correo.co"));
        assertThat(creado.getValue().tieneElCorreoVerificado()).isFalse();
        verify(correo).enviarVerificacionDeCorreo(any(), any());
    }

    // Criterio 2. Es la regla mas facil de romper por accidente en un refactor.
    @Test
    void no_deberia_revelar_que_el_correo_ya_existe() {
        when(filtradas.verificar(any())).thenReturn(BreachedPasswordChecker.Resultado.LIMPIA);
        when(hasher.hashear(any())).thenReturn(new PasswordHash("$argon2id$hash"));
        when(usuarios.buscarPorCorreo(any())).thenReturn(Optional.of(usuarioExistente()));

        assertThatCode(() -> caso.execute(comandoValido())).doesNotThrowAnyException();

        verify(usuarios, never()).crear(any(), any());
        verify(consentimientos, never()).guardarTodos(anyList());
        verifyNoInteractions(generadorDeTokens);
    }

    @Test
    void deberia_avisar_al_titular_cuando_alguien_intenta_registrar_su_correo() {
        when(filtradas.verificar(any())).thenReturn(BreachedPasswordChecker.Resultado.LIMPIA);
        when(hasher.hashear(any())).thenReturn(new PasswordHash("$argon2id$hash"));
        User titular = usuarioExistente();
        when(usuarios.buscarPorCorreo(any())).thenReturn(Optional.of(titular));

        caso.execute(comandoValido());

        verify(correo).enviarAvisoDeRegistroConCorreoExistente(titular);
        verify(correo, never()).enviarVerificacionDeCorreo(any(), any());
    }

    // El hash es lo caro. Si solo se calculara para cuentas nuevas, el tiempo de
    // respuesta distinguiria los dos caminos y el criterio 2 quedaria en nada.
    @Test
    void deberia_hashear_la_contrasena_tambien_cuando_el_correo_ya_existe() {
        when(filtradas.verificar(any())).thenReturn(BreachedPasswordChecker.Resultado.LIMPIA);
        when(hasher.hashear(any())).thenReturn(new PasswordHash("$argon2id$hash"));
        when(usuarios.buscarPorCorreo(any())).thenReturn(Optional.of(usuarioExistente()));

        caso.execute(comandoValido());

        verify(hasher).hashear(new RawPassword("una contrasena larga"));
    }

    @Test
    void deberia_rechazar_una_contrasena_de_menos_de_diez_caracteres_RN_005() {
        RegisterUserCommand corta = new RegisterUserCommand(
                "ana@correo.co", "corta", "Ana Maria", LocalDate.of(1990, 3, 4), "es", true, true, null);

        assertThatThrownBy(() -> caso.execute(corta)).isInstanceOf(PasswordTooShortException.class);

        verifyNoInteractions(usuarios, hasher);
    }

    @Test
    void deberia_rechazar_una_contrasena_filtrada_RN_005() {
        when(filtradas.verificar(any())).thenReturn(BreachedPasswordChecker.Resultado.FILTRADA);

        assertThatThrownBy(() -> caso.execute(comandoValido())).isInstanceOf(BreachedPasswordException.class);

        verify(usuarios, never()).crear(any(), any());
    }

    // ADR-0013: si el tercero no responde, el registro sigue. Su disponibilidad
    // no puede convertirse en la nuestra.
    @Test
    void deberia_aceptar_el_registro_cuando_no_se_puede_comprobar_la_filtracion_ADR_0013() {
        conCaminoFeliz();
        when(filtradas.verificar(any())).thenReturn(BreachedPasswordChecker.Resultado.NO_SE_PUDO_COMPROBAR);

        caso.execute(comandoValido());

        verify(usuarios).crear(any(), any());
    }

    // El minimo de diez caracteres no depende de nadie: se aplica aunque el
    // servicio externo este caido.
    @Test
    void deberia_seguir_exigiendo_el_largo_minimo_aunque_el_servicio_externo_falle_RN_005() {
        RegisterUserCommand corta = new RegisterUserCommand(
                "ana@correo.co", "corta", "Ana Maria", LocalDate.of(1990, 3, 4), "es", true, true, null);

        assertThatThrownBy(() -> caso.execute(corta)).isInstanceOf(PasswordTooShortException.class);
    }

    @Test
    void deberia_rechazar_el_registro_de_un_menor_de_edad_RN_008() {
        RegisterUserCommand menor = new RegisterUserCommand(
                "ana@correo.co", "una contrasena larga", "Ana Maria", LocalDate.of(2015, 3, 4), "es", true, true, null);
        when(filtradas.verificar(any())).thenReturn(BreachedPasswordChecker.Resultado.LIMPIA);

        assertThatThrownBy(() -> caso.execute(menor)).isInstanceOf(UnderageException.class);
    }

    // Si la edad se comprobara despues de buscar el correo, un menor con correo
    // ajeno recibiria 202 y con correo nuevo 422: eso delata que la cuenta existe.
    @Test
    void deberia_rechazar_al_menor_de_edad_antes_de_mirar_si_el_correo_existe_RN_008() {
        RegisterUserCommand menor = new RegisterUserCommand(
                "ana@correo.co", "una contrasena larga", "Ana Maria", LocalDate.of(2015, 3, 4), "es", true, true, null);
        when(filtradas.verificar(any())).thenReturn(BreachedPasswordChecker.Resultado.LIMPIA);

        assertThatThrownBy(() -> caso.execute(menor)).isInstanceOf(UnderageException.class);

        verify(usuarios, never()).buscarPorCorreo(any());
    }

    @Test
    void deberia_exigir_las_dos_casillas_de_consentimiento() {
        RegisterUserCommand soloTerminos = new RegisterUserCommand(
                "ana@correo.co", "una contrasena larga", "Ana", LocalDate.of(1990, 3, 4), "es", true, false, null);
        RegisterUserCommand soloPrivacidad = new RegisterUserCommand(
                "ana@correo.co", "una contrasena larga", "Ana", LocalDate.of(1990, 3, 4), "es", false, true, null);
        RegisterUserCommand ninguna = new RegisterUserCommand(
                "ana@correo.co", "una contrasena larga", "Ana", LocalDate.of(1990, 3, 4), "es", false, false, null);

        assertThatThrownBy(() -> caso.execute(soloTerminos)).isInstanceOf(ConsentRequiredException.class);
        assertThatThrownBy(() -> caso.execute(soloPrivacidad)).isInstanceOf(ConsentRequiredException.class);
        assertThatThrownBy(() -> caso.execute(ninguna)).isInstanceOf(ConsentRequiredException.class);
    }

    @Test
    void deberia_guardar_un_consentimiento_por_documento_con_su_version() {
        conCaminoFeliz();
        when(documentosLegales.versionVigente(ConsentDocument.TERMS)).thenReturn("terminos-2026-08");
        when(documentosLegales.versionVigente(ConsentDocument.PRIVACY)).thenReturn("privacidad-2026-08");

        caso.execute(comandoValido());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Consent>> guardados = ArgumentCaptor.forClass(List.class);
        verify(consentimientos).guardarTodos(guardados.capture());

        assertThat(guardados.getValue())
                .extracting(Consent::document, Consent::version, Consent::ipHash)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(ConsentDocument.TERMS, "terminos-2026-08", "hash-ip"),
                        org.assertj.core.groups.Tuple.tuple(ConsentDocument.PRIVACY, "privacidad-2026-08", "hash-ip"));
    }

    @Test
    void deberia_emitir_un_token_valido_veinticuatro_horas_RN_003() {
        conCaminoFeliz();

        caso.execute(comandoValido());

        ArgumentCaptor<VerificationToken> emitido = ArgumentCaptor.forClass(VerificationToken.class);
        verify(tokens).guardar(emitido.capture());

        assertThat(emitido.getValue().expiresAt()).isEqualTo(AHORA.plus(Duration.ofHours(24)));
    }

    // El valor en claro solo viaja al correo. Lo que se guarda es el hash.
    @Test
    void deberia_guardar_el_hash_del_token_y_enviar_por_correo_el_valor_en_claro() {
        conCaminoFeliz();

        caso.execute(comandoValido());

        ArgumentCaptor<VerificationToken> emitido = ArgumentCaptor.forClass(VerificationToken.class);
        verify(tokens).guardar(emitido.capture());
        assertThat(emitido.getValue().tokenHash()).isEqualTo("token-hash");

        verify(correo).enviarVerificacionDeCorreo(any(), org.mockito.ArgumentMatchers.eq("token-claro"));
    }

    @Test
    void deberia_normalizar_el_correo_antes_de_buscarlo_RN_001() {
        conCaminoFeliz();

        caso.execute(comandoValido());

        verify(usuarios).buscarPorCorreo(new Email("ana@correo.co"));
    }
}
