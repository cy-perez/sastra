package co.sastra.identity.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import co.sastra.identity.dto.ActiveSession;
import co.sastra.identity.model.RefreshToken;
import co.sastra.identity.model.UserId;
import co.sastra.identity.port.out.RefreshTokenRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Criterio 17. */
@ExtendWith(MockitoExtension.class)
class ListSessionsUseCaseTest {

    private static final Instant AHORA = Instant.parse("2026-08-17T15:00:00Z");

    @Mock
    private RefreshTokenRepository refrescos;

    private ListSessionsUseCase caso;
    private UserId usuario;

    @BeforeEach
    void prepararCaso() {
        caso = new ListSessionsUseCase(refrescos, Clock.fixed(AHORA, ZoneOffset.UTC));
        usuario = UserId.nuevo();
    }

    private RefreshToken sesion(String navegador) {
        return RefreshToken.abrirSesion(
                usuario, "hash-" + navegador, AHORA.minus(Duration.ofDays(1)), RefreshToken.VIGENCIA, navegador, "ip");
    }

    @Test
    void deberia_devolver_las_sesiones_con_su_navegador_y_sus_fechas() {
        RefreshToken enElPortatil = sesion("Firefox");
        when(refrescos.listarSesionesActivasDe(usuario, AHORA)).thenReturn(List.of(enElPortatil));

        List<ActiveSession> sesiones = caso.execute(usuario, null);

        assertThat(sesiones).hasSize(1);
        assertThat(sesiones.getFirst().userAgent()).isEqualTo("Firefox");
        assertThat(sesiones.getFirst().iniciada()).isEqualTo(enElPortatil.createdAt());
        assertThat(sesiones.getFirst().expira()).isEqualTo(enElPortatil.expiresAt());
    }

    /**
     * El identificador que sale es el de la familia y no el del token: es el que
     * sobrevive a las rotaciones, y por tanto el unico que sirve para cerrar una
     * sesion que lleva un mes refrescandose.
     */
    @Test
    void deberia_identificar_la_sesion_por_su_familia() {
        RefreshToken abierta = sesion("Chrome");
        when(refrescos.listarSesionesActivasDe(usuario, AHORA)).thenReturn(List.of(abierta));

        assertThat(caso.execute(usuario, null).getFirst().id())
                .isEqualTo(abierta.familyId().toString());
    }

    // Sin esto, cerrar la propia sesion desde la lista parece un fallo.
    @Test
    void deberia_marcar_cual_es_la_sesion_actual_criterio_17() {
        RefreshToken laDeAhora = sesion("Chrome");
        RefreshToken otra = sesion("Firefox");
        when(refrescos.listarSesionesActivasDe(usuario, AHORA)).thenReturn(List.of(laDeAhora, otra));

        List<ActiveSession> sesiones = caso.execute(usuario, laDeAhora.familyId());

        assertThat(sesiones.getFirst().actual()).isTrue();
        assertThat(sesiones.getLast().actual()).isFalse();
    }

    /**
     * Un token emitido antes de que existiera el claim no trae sesion. Entonces no
     * se marca ninguna, que es mejor que marcar la equivocada.
     */
    @Test
    void no_deberia_marcar_ninguna_si_el_token_no_dice_cual_es() {
        when(refrescos.listarSesionesActivasDe(usuario, AHORA)).thenReturn(List.of(sesion("Chrome")));

        assertThat(caso.execute(usuario, null)).allMatch(sesion -> !sesion.actual());
    }

    /**
     * La IP se guarda para reconocer patrones de ataque, no para ensenarsela a
     * nadie: un hash no le dice nada a quien mira la lista y sacarlo del servidor
     * convertiria un dato guardado con cuidado en uno que viaja.
     */
    @Test
    void nunca_deberia_exponer_la_ip() {
        when(refrescos.listarSesionesActivasDe(usuario, AHORA)).thenReturn(List.of(sesion("Chrome")));

        assertThat(caso.execute(usuario, null).getFirst().toString()).doesNotContain("ip");
    }
}
