package co.sendik.identity.usecase;

import co.sendik.identity.dto.LogoutCommand;
import co.sendik.identity.model.RefreshToken;
import co.sendik.identity.port.out.RefreshTokenRepository;
import co.sendik.identity.port.out.TokenGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cierre de sesion. Criterio 16: se revoca en el servidor, no solo en el
 * navegador.
 *
 * <p>Se revoca la familia entera y no solo el token presentado. Una familia es una
 * sesion, y la cookie del navegador puede llevar un token ya rotado: revocar solo
 * ese dejaria vivo a su descendiente, o sea la sesion abierta despues de haber
 * pulsado "salir".
 *
 * <p><strong>Nunca falla.</strong> Una cookie ausente, caducada o inventada
 * terminan igual: sin sesion. Responder un error a quien quiere salir no protege
 * nada y deja al cliente sin saber si limpiar su estado.
 */
public class LogoutUseCase {

    private final RefreshTokenRepository refrescos;
    private final TokenGenerator generadorDeTokens;
    private final Clock reloj;

    public LogoutUseCase(RefreshTokenRepository refrescos, TokenGenerator generadorDeTokens, Clock reloj) {
        this.refrescos = refrescos;
        this.generadorDeTokens = generadorDeTokens;
        this.reloj = reloj;
    }

    @Transactional
    public void execute(LogoutCommand comando) {
        String recibido = comando.refreshToken();
        if (recibido == null || recibido.isBlank()) {
            return;
        }

        Instant ahora = reloj.instant();
        String hash = generadorDeTokens.hashearRecibido(recibido);

        Optional<RefreshToken> presentado = refrescos.buscarPorHash(hash);
        presentado.ifPresent(token -> refrescos.revocarFamilia(token.familyId(), ahora));
    }
}
