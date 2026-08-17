package co.sastra.identity.usecase;

import co.sastra.identity.dto.IssueSessionCommand;
import co.sastra.identity.dto.SessionResult;
import co.sastra.identity.dto.VerifyEmailCommand;
import co.sastra.identity.dto.VerifyEmailResult;
import co.sastra.identity.exception.VerificationTokenInvalidException;
import co.sastra.identity.model.TokenPurpose;
import co.sastra.identity.model.User;
import co.sastra.identity.model.VerificationToken;
import co.sastra.identity.port.out.TokenGenerator;
import co.sastra.identity.port.out.UserRepository;
import co.sastra.identity.port.out.VerificationTokenRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;

/**
 * Activacion de la cuenta con el enlace del correo. Criterios 7 a 9 de HU-001.
 *
 * <p>El criterio 9 pide dos cosas: que la cuenta quede activa y que la persona
 * entre directamente. La sesion se emite en la misma transaccion que consume el
 * token, porque el enlace es de un solo uso: si se emitiera en una segunda llamada
 * y esa llamada se perdiera, el token ya estaria gastado y no habria forma de
 * recuperar la sesion.
 */
public class VerifyEmailUseCase {

    private final UserRepository usuarios;
    private final VerificationTokenRepository tokens;
    private final TokenGenerator generadorDeTokens;
    private final IssueSessionUseCase abrirSesion;
    private final Clock reloj;

    public VerifyEmailUseCase(
            UserRepository usuarios,
            VerificationTokenRepository tokens,
            TokenGenerator generadorDeTokens,
            IssueSessionUseCase abrirSesion,
            Clock reloj) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.generadorDeTokens = generadorDeTokens;
        this.abrirSesion = abrirSesion;
        this.reloj = reloj;
    }

    @Transactional
    public VerifyEmailResult execute(VerifyEmailCommand comando) {
        Instant ahora = reloj.instant();

        // El valor que llega en el enlace se hashea antes de consultar: la base
        // guarda el hash y nunca ve el original.
        String hash = generadorDeTokens.hashearRecibido(comando.token());

        VerificationToken token = tokens.buscarPorHash(hash).orElseThrow(VerificationTokenInvalidException::new);

        if (token.purpose() != TokenPurpose.EMAIL_VERIFICATION) {
            // Un token de restablecimiento no activa una cuenta. Mezclar los
            // propositos convertiria un enlace en otro.
            throw new VerificationTokenInvalidException();
        }

        token.verificarUtilizable(ahora);

        User usuario = usuarios.buscarPorId(token.userId()).orElseThrow(VerificationTokenInvalidException::new);
        boolean yaEstabaVerificado = usuario.tieneElCorreoVerificado();

        User verificado = usuario.conCorreoVerificado(ahora);
        usuarios.actualizar(verificado);
        tokens.actualizar(token.marcarUsado(ahora));

        // Con el usuario ya verificado, no con el de antes: el token de acceso lleva
        // el estado del correo y tiene que nacer diciendo que la cuenta esta activa.
        SessionResult sesion =
                abrirSesion.execute(new IssueSessionCommand(verificado, comando.userAgent(), comando.ipHash()));

        return new VerifyEmailResult(sesion, yaEstabaVerificado);
    }
}
