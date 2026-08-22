package co.sastra.identity.usecase;

import co.sastra.identity.dto.ApproveVerificationCommand;
import co.sastra.identity.exception.VerificationNotFoundException;
import co.sastra.identity.model.Role;
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
 * <p>Lo que <strong>no</strong> comprueba nadie todavia: que el moderador no sea la
 * misma persona que la solicitud. Ninguna regla de negocio lo prohibe por escrito y no
 * se inventa aqui; queda anotado en HU-002 como decision pendiente.
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
