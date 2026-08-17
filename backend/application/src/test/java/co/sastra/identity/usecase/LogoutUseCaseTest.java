package co.sastra.identity.usecase;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.sastra.identity.dto.LogoutCommand;
import co.sastra.identity.model.RefreshToken;
import co.sastra.identity.model.UserId;
import co.sastra.identity.port.out.RefreshTokenRepository;
import co.sastra.identity.port.out.TokenGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LogoutUseCaseTest {

    private static final Instant AHORA = Instant.parse("2026-08-17T15:00:00Z");

    @Mock
    private RefreshTokenRepository refrescos;

    @Mock
    private TokenGenerator generadorDeTokens;

    private LogoutUseCase caso;
    private RefreshToken sesion;

    @BeforeEach
    void prepararCaso() {
        caso = new LogoutUseCase(refrescos, generadorDeTokens, Clock.fixed(AHORA, ZoneOffset.UTC));
        sesion = RefreshToken.abrirSesion(
                UserId.nuevo(),
                "hash-vigente",
                AHORA.minus(Duration.ofDays(1)),
                RefreshToken.VIGENCIA,
                "Firefox",
                "ip");
    }

    // Criterio 16: se revoca en el servidor, no solo en el navegador. Y se revoca
    // la familia, porque una familia es una sesion: revocar solo el token que traia
    // la cookie dejaria vivo a su descendiente.
    @Test
    void deberia_revocar_la_familia_completa_criterio_16() {
        when(generadorDeTokens.hashearRecibido("refresco-en-claro")).thenReturn("hash-vigente");
        when(refrescos.buscarPorHash("hash-vigente")).thenReturn(Optional.of(sesion));

        caso.execute(new LogoutCommand("refresco-en-claro"));

        verify(refrescos).revocarFamilia(sesion.familyId(), AHORA);
    }

    @Test
    void deberia_revocar_tambien_una_sesion_cuyo_token_ya_se_habia_rotado() {
        RefreshToken consumido = sesion.rotar(
                        "hash-siguiente", AHORA.minusSeconds(60), RefreshToken.VIGENCIA, null, null)
                .consumido();
        when(generadorDeTokens.hashearRecibido("refresco-en-claro")).thenReturn("hash-vigente");
        when(refrescos.buscarPorHash("hash-vigente")).thenReturn(Optional.of(consumido));

        caso.execute(new LogoutCommand("refresco-en-claro"));

        verify(refrescos).revocarFamilia(sesion.familyId(), AHORA);
    }

    // Cerrar sesion sin cookie es normal, no un error: el navegador puede haberla
    // borrado. Responder un error dejaria al cliente sin saber si limpiar su estado.
    @Test
    void no_deberia_fallar_sin_cookie() {
        caso.execute(new LogoutCommand(null));
        caso.execute(new LogoutCommand("   "));

        verifyNoInteractions(refrescos, generadorDeTokens);
    }

    @Test
    void no_deberia_fallar_con_una_cookie_que_no_corresponde_a_ninguna_sesion() {
        when(generadorDeTokens.hashearRecibido("inventado")).thenReturn("hash-inventado");
        when(refrescos.buscarPorHash("hash-inventado")).thenReturn(Optional.empty());

        caso.execute(new LogoutCommand("inventado"));

        verify(refrescos, never()).revocarFamilia(any(), any());
    }

    @Test
    void deberia_buscar_por_el_hash_y_no_por_el_valor_de_la_cookie() {
        when(generadorDeTokens.hashearRecibido("refresco-en-claro")).thenReturn("hash-vigente");
        when(refrescos.buscarPorHash("hash-vigente")).thenReturn(Optional.of(sesion));

        caso.execute(new LogoutCommand("refresco-en-claro"));

        verify(refrescos, never()).buscarPorHash("refresco-en-claro");
    }
}
