package co.sastra.identity.usecase;

import static org.mockito.Mockito.verify;

import co.sastra.identity.model.TokenFamilyId;
import co.sastra.identity.model.UserId;
import co.sastra.identity.port.out.RefreshTokenRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Criterio 17: cerrar una sesion desde la lista.
 *
 * <p>Lo que hay que demostrar aqui es que el usuario viaja hasta el repositorio.
 * Revocar por familia sin acotar a la persona convertiria el identificador de
 * sesion de la lista en una forma de cerrarle la sesion a cualquiera que la
 * tuviera abierta: el dueno no es un parametro decorativo, es la autorizacion. El
 * repositorio hace el filtro, pero solo puede hacerlo si el caso se lo pasa.
 *
 * <p>El momento sale del reloj inyectado y no de {@code Instant.now()}, que es lo
 * que permite fijarlo aqui y lo que impide que la revocacion quede fechada por el
 * reloj de la maquina.
 */
@ExtendWith(MockitoExtension.class)
class RevokeSessionUseCaseTest {

    private static final Instant AHORA = Instant.parse("2026-08-20T15:00:00Z");

    @Mock
    private RefreshTokenRepository refrescos;

    private RevokeSessionUseCase caso;

    @BeforeEach
    void prepararCaso() {
        caso = new RevokeSessionUseCase(refrescos, Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    void deberia_revocar_la_sesion_de_esa_persona_y_no_la_familia_a_secas() {
        UserId usuario = UserId.nuevo();
        TokenFamilyId sesion = TokenFamilyId.nueva();

        caso.execute(usuario, sesion);

        verify(refrescos).revocarSesionDe(usuario, sesion, AHORA);
    }
}
