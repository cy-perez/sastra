package co.sastra.identity.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.sastra.identity.dto.IssueSessionCommand;
import co.sastra.identity.dto.SessionResult;
import co.sastra.identity.model.BirthDate;
import co.sastra.identity.model.DisplayName;
import co.sastra.identity.model.Email;
import co.sastra.identity.model.RefreshToken;
import co.sastra.identity.model.Role;
import co.sastra.identity.model.User;
import co.sastra.identity.model.UserId;
import co.sastra.identity.model.UserLocale;
import co.sastra.identity.port.out.AccessTokenIssuer;
import co.sastra.identity.port.out.RefreshTokenRepository;
import co.sastra.identity.port.out.TokenGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IssueSessionUseCaseTest {

    private static final Instant AHORA = Instant.parse("2026-08-17T15:00:00Z");

    @Mock
    private RefreshTokenRepository refrescos;

    @Mock
    private AccessTokenIssuer accesos;

    @Mock
    private TokenGenerator generadorDeTokens;

    private IssueSessionUseCase caso;
    private User usuario;

    @BeforeEach
    void prepararCaso() {
        caso = new IssueSessionUseCase(
                refrescos, accesos, generadorDeTokens, RefreshToken.VIGENCIA, Clock.fixed(AHORA, ZoneOffset.UTC));

        usuario = User.registrar(
                UserId.nuevo(),
                new Email("ana@correo.co"),
                new DisplayName("Ana Maria"),
                new BirthDate(LocalDate.of(1990, 3, 4)),
                UserLocale.ES,
                LocalDate.of(2026, 8, 17),
                AHORA.minus(Duration.ofDays(10)));
    }

    private void conTokensGenerados() {
        when(generadorDeTokens.generar())
                .thenReturn(new TokenGenerator.GeneratedToken("refresco-en-claro", "hash-del-refresco"));
        when(accesos.emitir(usuario, AHORA))
                .thenReturn(
                        new AccessTokenIssuer.IssuedAccessToken("token-de-acceso", AHORA.plus(Duration.ofMinutes(15))));
    }

    @Test
    void deberia_devolver_los_dos_tokens_y_el_resumen_del_usuario() {
        conTokensGenerados();

        SessionResult sesion = caso.execute(new IssueSessionCommand(usuario, "Firefox", "ip-hash"));

        assertThat(sesion.accessToken()).isEqualTo("token-de-acceso");
        assertThat(sesion.accessTokenExpiresAt()).isEqualTo(AHORA.plus(Duration.ofMinutes(15)));
        assertThat(sesion.refreshToken()).isEqualTo("refresco-en-claro");
        assertThat(sesion.refreshTokenExpiresAt()).isEqualTo(AHORA.plus(Duration.ofDays(30)));
        assertThat(sesion.user().id()).isEqualTo(usuario.id());
        assertThat(sesion.user().email()).isEqualTo("ana@correo.co");
        assertThat(sesion.user().displayName()).isEqualTo("Ana Maria");
        assertThat(sesion.user().emailVerified()).isFalse();
        assertThat(sesion.user().roles()).containsExactly(Role.BUYER);
    }

    // Lo que se guarda es el hash. El valor en claro solo viaja hacia la cookie.
    @Test
    void deberia_guardar_solo_el_hash_del_token_de_refresco() {
        conTokensGenerados();

        caso.execute(new IssueSessionCommand(usuario, "Firefox", "ip-hash"));

        ArgumentCaptor<RefreshToken> guardado = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refrescos).guardar(guardado.capture());
        assertThat(guardado.getValue().tokenHash()).isEqualTo("hash-del-refresco");
        assertThat(guardado.getValue().tokenHash()).isNotEqualTo("refresco-en-claro");
    }

    @Test
    void deberia_abrir_una_familia_nueva_por_sesion_RN_007() {
        conTokensGenerados();

        caso.execute(new IssueSessionCommand(usuario, null, null));
        caso.execute(new IssueSessionCommand(usuario, null, null));

        ArgumentCaptor<RefreshToken> guardados = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refrescos, org.mockito.Mockito.times(2)).guardar(guardados.capture());
        assertThat(guardados.getAllValues().get(0).familyId())
                .isNotEqualTo(guardados.getAllValues().get(1).familyId());
    }

    @Test
    void deberia_guardar_el_navegador_y_la_ip_de_la_sesion() {
        conTokensGenerados();

        caso.execute(new IssueSessionCommand(usuario, "Firefox", "ip-hash"));

        ArgumentCaptor<RefreshToken> guardado = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refrescos).guardar(guardado.capture());
        assertThat(guardado.getValue().userAgent()).isEqualTo("Firefox");
        assertThat(guardado.getValue().ipHash()).isEqualTo("ip-hash");
        assertThat(guardado.getValue().userId()).isEqualTo(usuario.id());
    }

    // La vigencia sale de la configuracion, no de la constante del dominio: es lo
    // que permite acortarla en un entorno de pruebas sin recompilar.
    @Test
    void deberia_respetar_la_vigencia_configurada() {
        IssueSessionUseCase conVigenciaCorta = new IssueSessionUseCase(
                refrescos, accesos, generadorDeTokens, Duration.ofHours(2), Clock.fixed(AHORA, ZoneOffset.UTC));
        conTokensGenerados();

        SessionResult sesion = conVigenciaCorta.execute(new IssueSessionCommand(usuario, null, null));

        assertThat(sesion.refreshTokenExpiresAt()).isEqualTo(AHORA.plus(Duration.ofHours(2)));
    }

    @Test
    void deberia_reflejar_el_correo_verificado_en_el_resumen() {
        User verificado = usuario.conCorreoVerificado(AHORA.minus(Duration.ofDays(1)));
        when(generadorDeTokens.generar())
                .thenReturn(new TokenGenerator.GeneratedToken("refresco-en-claro", "hash-del-refresco"));
        when(accesos.emitir(verificado, AHORA))
                .thenReturn(
                        new AccessTokenIssuer.IssuedAccessToken("token-de-acceso", AHORA.plus(Duration.ofMinutes(15))));

        SessionResult sesion = caso.execute(new IssueSessionCommand(verificado, null, null));

        assertThat(sesion.user().emailVerified()).isTrue();
    }
}
