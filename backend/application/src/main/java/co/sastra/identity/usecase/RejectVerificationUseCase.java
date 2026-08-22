package co.sastra.identity.usecase;

import co.sastra.identity.dto.RejectVerificationCommand;
import co.sastra.identity.exception.SelfReviewForbiddenException;
import co.sastra.identity.exception.VerificationNotFoundException;
import co.sastra.identity.model.SellerVerification;
import co.sastra.identity.model.VerificationAccess;
import co.sastra.identity.port.out.MailSender;
import co.sastra.identity.port.out.SellerVerificationRepository;
import co.sastra.identity.port.out.UserRepository;
import co.sastra.identity.port.out.VerificationAccessLog;
import java.time.Clock;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;

/**
 * El moderador rechaza con un motivo de la lista cerrada. Criterio 7 de HU-002.
 *
 * <p>No toca roles: quien es rechazado nunca los tuvo. Lo que si queda es el intento
 * gastado —lo conto el envio— y el motivo, que la persona necesita para corregir.
 *
 * <p>El motivo va a la bitacora ademas de a la solicitud. En la solicitud describe su
 * estado actual y se pierde al reintentar; en la bitacora queda la secuencia de
 * decisiones, que es lo que permite revisar por que se rechazo tres veces a alguien.
 */
public class RejectVerificationUseCase {

    private final SellerVerificationRepository verificaciones;
    private final UserRepository usuarios;
    private final VerificationAccessLog bitacora;
    private final MailSender correo;
    private final Clock reloj;

    public RejectVerificationUseCase(
            SellerVerificationRepository verificaciones,
            UserRepository usuarios,
            VerificationAccessLog bitacora,
            MailSender correo,
            Clock reloj) {
        this.verificaciones = verificaciones;
        this.usuarios = usuarios;
        this.bitacora = bitacora;
        this.correo = correo;
        this.reloj = reloj;
    }

    @Transactional
    public SellerVerification execute(RejectVerificationCommand comando) {
        SellerVerification actual = verificaciones
                .buscarPorId(comando.verificacion())
                .orElseThrow(() -> new VerificationNotFoundException(comando.verificacion()));

        // RN-060. Antes de tocar nada: quien revisa y quien es revisado tienen que ser
        // dos personas. Va aqui y no en el borde HTTP porque el borde comprueba el rol
        // —que lo tiene— y no sabe de quien es la solicitud.
        if (actual.userId().equals(comando.moderador())) {
            throw new SelfReviewForbiddenException();
        }

        Instant ahora = reloj.instant();

        SellerVerification rechazada = actual.rechazar(comando.motivo(), comando.nota(), ahora);
        verificaciones.guardar(rechazada);

        bitacora.registrar(
                rechazada.id(),
                comando.moderador(),
                VerificationAccess.REJECT,
                comando.motivo().name(),
                ahora);

        // Criterio 10. Lleva los intentos que quedan: en cero el correo no invita a
        // reintentar, porque RN-014 no lo permite y prometerlo seria mandar a alguien a
        // una negativa.
        usuarios.buscarPorId(rechazada.userId())
                .ifPresent(titular -> correo.enviarAvisoDeVerificacionRechazada(
                        titular,
                        comando.motivo(),
                        comando.nota(),
                        SellerVerification.MAXIMO_INTENTOS - rechazada.attempts()));

        return rechazada;
    }
}
