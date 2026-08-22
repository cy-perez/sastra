package co.sastra.identity.usecase;

import co.sastra.identity.dto.StartSellerVerificationCommand;
import co.sastra.identity.exception.AccountNoLongerExistsException;
import co.sastra.identity.exception.EmailNotVerifiedException;
import co.sastra.identity.exception.UnderageException;
import co.sastra.identity.model.SellerVerification;
import co.sastra.identity.model.SellerVerificationId;
import co.sastra.identity.model.User;
import co.sastra.identity.port.out.SellerVerificationRepository;
import co.sastra.identity.port.out.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Empieza el proceso de verificacion. Criterio 1 de HU-002.
 *
 * <p><strong>Es idempotente.</strong> Quien ya lo tenia empezado recibe lo que
 * llevaba, no una solicitud nueva: el caso borde de la historia dice que se guarda el
 * avance y se retoma donde iba, y crear otra fila lo tiraria. Tampoco reinicia una
 * rechazada: para eso esta reintentar, que cuenta el intento de RN-014.
 *
 * <p>Las dos condiciones del criterio 1 se comprueban aqui aunque las dos esten ya
 * garantizadas en otra parte —RN-008 rechaza al menor al registrarse y el correo se
 * verifica con su propio enlace—. Se comprueban igual porque el dominio se valida a
 * si mismo: si manana el registro cambia, esta puerta sigue cerrada.
 */
public class StartSellerVerificationUseCase {

    private final UserRepository usuarios;
    private final SellerVerificationRepository verificaciones;
    private final Clock reloj;

    public StartSellerVerificationUseCase(
            UserRepository usuarios, SellerVerificationRepository verificaciones, Clock reloj) {
        this.usuarios = usuarios;
        this.verificaciones = verificaciones;
        this.reloj = reloj;
    }

    @Transactional
    public SellerVerification execute(StartSellerVerificationCommand comando) {
        User cuenta = usuarios.buscarPorId(comando.usuario()).orElseThrow(AccountNoLongerExistsException::new);

        if (!cuenta.tieneElCorreoVerificado()) {
            throw new EmailNotVerifiedException();
        }
        if (!cuenta.birthDate().esMayorDeEdad(LocalDate.now(reloj))) {
            throw new UnderageException();
        }

        return verificaciones.buscarPorUsuario(comando.usuario()).orElseGet(() -> crear(comando));
    }

    private SellerVerification crear(StartSellerVerificationCommand comando) {
        SellerVerification nueva =
                SellerVerification.iniciar(SellerVerificationId.nuevo(), comando.usuario(), reloj.instant());

        verificaciones.guardar(nueva);
        return nueva;
    }
}
