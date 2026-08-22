package co.sastra.identity.usecase;

import co.sastra.identity.dto.SubmitVerificationForReviewCommand;
import co.sastra.identity.exception.DocumentAlreadyVerifiedException;
import co.sastra.identity.exception.InvalidVerificationTransitionException;
import co.sastra.identity.model.IdentityDocument;
import co.sastra.identity.model.SellerVerification;
import co.sastra.identity.model.VerificationStatus;
import co.sastra.identity.port.out.MailSender;
import co.sastra.identity.port.out.SellerVerificationRepository;
import co.sastra.identity.port.out.UserRepository;
import java.time.Clock;
import org.springframework.transaction.annotation.Transactional;

/**
 * Envia la solicitud a revision. Criterio 6 de HU-002.
 *
 * <p>Que esten los tres datos, que el titular coincida (RN-012) y que queden intentos
 * (RN-014) lo comprueba el agregado. Lo que se comprueba aqui es el criterio 5, que
 * necesita mirar las demas solicitudes.
 *
 * <p><strong>Se vuelve a comprobar aunque ya se comprobara al subir el
 * documento.</strong> Entre las dos cosas pueden pasar dias —el proceso se retoma
 * donde iba— y en ese hueco otra cuenta puede haber quedado verificada con el mismo
 * documento. Sin esta segunda comprobacion, la solicitud llegaria a la bandeja del
 * moderador para que la rechace por algo que el sistema ya sabia.
 */
public class SubmitVerificationForReviewUseCase {

    private final SellerVerificationRepository verificaciones;
    private final UserRepository usuarios;
    private final MailSender correo;
    private final Clock reloj;

    public SubmitVerificationForReviewUseCase(
            SellerVerificationRepository verificaciones, UserRepository usuarios, MailSender correo, Clock reloj) {
        this.verificaciones = verificaciones;
        this.usuarios = usuarios;
        this.correo = correo;
        this.reloj = reloj;
    }

    @Transactional
    public SellerVerification execute(SubmitVerificationForReviewCommand comando) {
        SellerVerification actual = verificaciones
                .buscarPorUsuario(comando.usuario())
                .orElseThrow(() -> new InvalidVerificationTransitionException(
                        VerificationStatus.NOT_STARTED, VerificationStatus.PENDING_REVIEW));

        IdentityDocument documento = actual.document();
        if (documento != null
                && verificaciones.existeOtraVerificadaConDocumento(
                        documento.number().value(), comando.usuario())) {
            throw new DocumentAlreadyVerifiedException();
        }

        SellerVerification enviada = actual.enviarARevision(reloj.instant());
        verificaciones.guardar(enviada);

        // Criterio 10. Despues de guardar: un aviso de algo que no se guardo es peor que
        // no avisar. El puerto no lanza si el correo no sale (ADR-0012), asi que esto no
        // puede tumbar el envio de la solicitud.
        usuarios.buscarPorId(comando.usuario()).ifPresent(correo::enviarAvisoDeVerificacionRecibida);

        return enviada;
    }
}
