package co.sastra.identity.usecase;

import co.sastra.identity.dto.ResendVerificationCommand;
import co.sastra.identity.exception.ResendLimitReachedException;
import co.sastra.identity.exception.VerificationTokenInvalidException;
import co.sastra.identity.model.TokenPurpose;
import co.sastra.identity.model.User;
import co.sastra.identity.model.VerificationToken;
import co.sastra.identity.port.out.MailSender;
import co.sastra.identity.port.out.TokenGenerator;
import co.sastra.identity.port.out.UserRepository;
import co.sastra.identity.port.out.VerificationTokenRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reenvio del correo de verificacion cuando el enlace caduco. Criterio 8.
 *
 * <p>Se identifica por el token caducado y no por el correo. Es lo que permite
 * informar honestamente del limite de reenvios: como el token demuestra que la
 * cuenta existe, decir "ya no puedes reenviar" no revela nada que quien tiene el
 * enlace no supiera. Un reenvio que aceptara un correo cualquiera tendria que
 * responder siempre lo mismo para no delatar quien esta registrado, y entonces
 * nadie sabria por que no le llega el mensaje.
 */
public class ResendVerificationUseCase {

    /** Criterio 8: tres por hora. */
    public static final int MAXIMO_REENVIOS_POR_HORA = 3;

    private static final Duration VENTANA = Duration.ofHours(1);

    private final UserRepository usuarios;
    private final VerificationTokenRepository tokens;
    private final TokenGenerator generadorDeTokens;
    private final MailSender correo;
    private final Clock reloj;

    public ResendVerificationUseCase(
            UserRepository usuarios,
            VerificationTokenRepository tokens,
            TokenGenerator generadorDeTokens,
            MailSender correo,
            Clock reloj) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.generadorDeTokens = generadorDeTokens;
        this.correo = correo;
        this.reloj = reloj;
    }

    @Transactional
    public void execute(ResendVerificationCommand comando) {
        Instant ahora = reloj.instant();

        String hash = generadorDeTokens.hashearRecibido(comando.expiredToken());
        VerificationToken anterior = tokens.buscarPorHash(hash).orElseThrow(VerificationTokenInvalidException::new);

        if (anterior.purpose() != TokenPurpose.EMAIL_VERIFICATION) {
            throw new VerificationTokenInvalidException();
        }

        User usuario = usuarios.buscarPorId(anterior.userId()).orElseThrow(VerificationTokenInvalidException::new);

        if (usuario.tieneElCorreoVerificado()) {
            // No hay nada que reenviar. Se responde como token invalido para no
            // convertir este endpoint en una forma de averiguar el estado ajeno.
            throw new VerificationTokenInvalidException();
        }

        int emitidosEnLaUltimaHora =
                tokens.contarEmitidosDesde(usuario.id(), TokenPurpose.EMAIL_VERIFICATION, ahora.minus(VENTANA));
        if (emitidosEnLaUltimaHora >= MAXIMO_REENVIOS_POR_HORA) {
            throw new ResendLimitReachedException();
        }

        TokenGenerator.GeneratedToken generado = generadorDeTokens.generar();
        tokens.guardar(VerificationToken.emitir(
                usuario.id(),
                TokenPurpose.EMAIL_VERIFICATION,
                generado.hash(),
                ahora,
                VerificationToken.VIGENCIA_VERIFICACION_DE_CORREO));

        correo.enviarVerificacionDeCorreo(usuario, generado.valorEnClaro());
    }
}
