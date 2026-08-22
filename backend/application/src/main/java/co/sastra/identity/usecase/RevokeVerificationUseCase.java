package co.sastra.identity.usecase;

import co.sastra.identity.dto.RevokeVerificationCommand;
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
 * Revoca el sello de quien ya estaba verificado. RN-013.
 *
 * <p>Es el espejo de aprobar: el estado pasa a {@code REVOKED}, se quita el rol
 * {@code SELLER} y queda la anotacion. Las tres en la misma transaccion, porque un
 * sello revocado en la solicitud pero con el rol todavia puesto es exactamente lo que
 * RN-013 quiere impedir.
 *
 * <p><strong>Lo que esta regla dice de las publicaciones no se cumple aqui.</strong>
 * RN-013 exige que las publicaciones activas sigan visibles y que no pueda crear
 * nuevas. Lo primero pasa solo, porque nadie las toca; lo segundo lo tiene que
 * comprobar el contexto de catalogo cuando exista, mirando el rol. Quitarlo es lo que
 * lo hace posible, y por eso esto es lo unico que hace falta hoy.
 */
public class RevokeVerificationUseCase {

    private final SellerVerificationRepository verificaciones;
    private final UserRepository usuarios;
    private final VerificationAccessLog bitacora;
    private final MailSender correo;
    private final Clock reloj;

    public RevokeVerificationUseCase(
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
    public SellerVerification execute(RevokeVerificationCommand comando) {
        SellerVerification actual = verificaciones
                .buscarPorId(comando.verificacion())
                .orElseThrow(() -> new VerificationNotFoundException(comando.verificacion()));

        Instant ahora = reloj.instant();

        SellerVerification revocada = actual.revocar(comando.motivo(), comando.nota(), ahora);
        verificaciones.guardar(revocada);

        usuarios.revocarRol(revocada.userId(), Role.SELLER);

        bitacora.registrar(
                revocada.id(),
                comando.moderador(),
                VerificationAccess.REVOKE,
                comando.motivo().name(),
                ahora);

        // RN-013 y criterio 10. El aviso dice que lo publicado sigue visible: sin esa
        // frase, quien lo reciba no sabe si perdio lo que tenia.
        usuarios.buscarPorId(revocada.userId())
                .ifPresent(
                        titular -> correo.enviarAvisoDeVerificacionRevocada(titular, comando.motivo(), comando.nota()));

        return revocada;
    }
}
