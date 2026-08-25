package co.sendik.identity.usecase;

import co.sendik.identity.dto.ApproveVerificationCommand;
import co.sendik.identity.exception.SelfReviewForbiddenException;
import co.sendik.identity.exception.VerificationNotFoundException;
import co.sendik.identity.model.Role;
import co.sendik.identity.model.SellerVerification;
import co.sendik.identity.model.VerificationAccess;
import co.sendik.identity.port.out.MailSender;
import co.sendik.identity.port.out.SellerVerificationRepository;
import co.sendik.identity.port.out.UserRepository;
import co.sendik.identity.port.out.VerificationAccessLog;
import java.time.Clock;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;

/**
 * El moderador aprueba. Criterio 8 de HU-002.
 *
 * <p>Tres cosas en una transaccion, y las tres tienen que ir juntas: la solicitud pasa
 * a {@code VERIFIED}, la cuenta recibe el rol {@code SELLER}, y queda la anotacion en
 * la bitacora. Si el rol se otorgara fuera de la transaccion y fallara, habria una
 * verificacion aprobada cuyo dueno no puede publicar, y nadie lo notaria hasta que esa
 * persona intentara vender.
 *
 * <p><strong>La autorizacion no esta aqui.</strong> Que quien llama sea moderador lo
 * declara el borde HTTP, que es donde este proyecto pone la autorizacion de cada
 * endpoint (backend/CLAUDE.md, ADR-0003). Lo que si sale del token y nunca de la
 * peticion es el actor, porque es lo que queda escrito en la bitacora.
 *
 * <p><strong>RN-060 si esta aqui</strong>, y es la excepcion a lo anterior: que el
 * moderador no sea el dueno de la solicitud no lo puede comprobar el borde, que sabe
 * el rol pero no de quien es la solicitud.
 *
 * <p>El sello y el rol se otorgan; el correo de aviso del criterio 10 llega con su
 * propia rebanada.
 */
public class ApproveVerificationUseCase {

    private final SellerVerificationRepository verificaciones;
    private final UserRepository usuarios;
    private final VerificationAccessLog bitacora;
    private final MailSender correo;
    private final Clock reloj;

    public ApproveVerificationUseCase(
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
    public SellerVerification execute(ApproveVerificationCommand comando) {
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

        SellerVerification aprobada = actual.aprobar(ahora);
        verificaciones.guardar(aprobada);

        usuarios.otorgarRol(aprobada.userId(), Role.SELLER, ahora);

        bitacora.registrar(aprobada.id(), comando.moderador(), VerificationAccess.APPROVE, null, ahora);

        // Criterio 10. Al final y no antes: la bitacora es lo unico que no puede faltar,
        // y un correo caido no puede impedir que la aprobacion quede escrita.
        usuarios.buscarPorId(aprobada.userId()).ifPresent(correo::enviarAvisoDeVerificacionAprobada);

        return aprobada;
    }
}
