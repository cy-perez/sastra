package co.sastra.identity.usecase;

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
 * <p>El criterio 9 dice ademas que la persona "entra directamente". Eso exige
 * emitir la sesion, que es la maquinaria de la rebanada B: aqui la cuenta queda
 * activa y el ingreso automatico se conecta cuando existan los tokens. Esta
 * anotado en el plan, no omitido en silencio.
 */
public class VerifyEmailUseCase {

    private final UserRepository usuarios;
    private final VerificationTokenRepository tokens;
    private final TokenGenerator generadorDeTokens;
    private final Clock reloj;

    public VerifyEmailUseCase(
            UserRepository usuarios,
            VerificationTokenRepository tokens,
            TokenGenerator generadorDeTokens,
            Clock reloj) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.generadorDeTokens = generadorDeTokens;
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

        usuarios.actualizar(usuario.conCorreoVerificado(ahora));
        tokens.actualizar(token.marcarUsado(ahora));

        return new VerifyEmailResult(usuario.id(), usuario.email().value(), yaEstabaVerificado);
    }
}
