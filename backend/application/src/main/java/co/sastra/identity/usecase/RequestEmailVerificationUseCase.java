package co.sastra.identity.usecase;

import co.sastra.identity.dto.RequestEmailVerificationCommand;
import co.sastra.identity.exception.ResendLimitReachedException;
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
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enlace de verificacion nuevo pedido desde una sesion abierta. Criterio 13.
 *
 * <p>Es la otra puerta del reenvio. {@link ResendVerificationUseCase} identifica a
 * la persona por el enlace caducado que tiene en la mano; este la identifica por su
 * sesion, que es lo que le queda a quien perdio el correo entero.
 *
 * <p>Comparte el limite de tres por hora, y lo comparte de verdad: cuenta los
 * tokens emitidos, no las veces que se llamo a un endpoint. Si contara por
 * endpoint, alternar entre los dos daria seis enlaces por hora.
 *
 * <p>Con el correo ya verificado no hace nada y no falla. Es una peticion sin
 * sentido, no un error: la pantalla que la origina solo existe mientras la cuenta
 * esta sin verificar, y responder un error a un doble clic tardio no ayuda a nadie.
 */
public class RequestEmailVerificationUseCase {

    private static final Duration VENTANA = Duration.ofHours(1);

    private final UserRepository usuarios;
    private final VerificationTokenRepository tokens;
    private final TokenGenerator generadorDeTokens;
    private final MailSender correo;
    private final Clock reloj;

    public RequestEmailVerificationUseCase(
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
    public void execute(RequestEmailVerificationCommand comando) {
        Instant ahora = reloj.instant();

        Optional<User> encontrado = usuarios.buscarPorId(comando.userId());
        if (encontrado.isEmpty()) {
            // El identificador viene del contexto de seguridad, asi que esto solo
            // ocurre si la cuenta se cerro con el token de acceso todavia vivo.
            return;
        }

        User usuario = encontrado.get();
        if (usuario.tieneElCorreoVerificado()) {
            return;
        }

        int emitidosEnLaUltimaHora =
                tokens.contarEmitidosDesde(usuario.id(), TokenPurpose.EMAIL_VERIFICATION, ahora.minus(VENTANA));
        if (emitidosEnLaUltimaHora >= ResendVerificationUseCase.MAXIMO_REENVIOS_POR_HORA) {
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
