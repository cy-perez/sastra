package co.sastra.identity.usecase;

import co.sastra.identity.dto.RequestEmailChangeCommand;
import co.sastra.identity.exception.AccountNoLongerExistsException;
import co.sastra.identity.model.Email;
import co.sastra.identity.model.User;
import co.sastra.identity.model.VerificationToken;
import co.sastra.identity.port.out.MailSender;
import co.sastra.identity.port.out.TokenGenerator;
import co.sastra.identity.port.out.UserRepository;
import co.sastra.identity.port.out.VerificationTokenRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pide cambiar el correo. Criterio 21, primera mitad.
 *
 * <p><strong>No reemplaza nada.</strong> Emite un enlace a la direccion nueva y
 * ahi acaba: el correo solo cambia cuando alguien demuestra que ese buzon es
 * suyo. Al reves, quien escribiera mal una letra se quedaria fuera de su propia
 * cuenta sin forma de volver.
 *
 * <p><strong>Responde igual este o no ocupada la direccion.</strong> Es la misma
 * regla del criterio 2 en el registro y por el mismo motivo: si dijera "ya tiene
 * cuenta", cualquiera con una cuenta propia podria averiguar quien esta
 * registrado en Sastra probando direcciones desde su perfil. Cuando esta ocupada
 * no se manda el enlace y se avisa a su titular, que es quien tiene derecho a
 * saber que alguien intento mudar una cuenta a su correo.
 */
public class RequestEmailChangeUseCase {

    private final UserRepository usuarios;
    private final VerificationTokenRepository tokens;
    private final TokenGenerator generadorDeTokens;
    private final MailSender correo;
    private final Clock reloj;

    public RequestEmailChangeUseCase(
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
    public void execute(RequestEmailChangeCommand comando) {
        Instant ahora = reloj.instant();

        User cuenta = usuarios.buscarPorId(comando.usuario()).orElseThrow(AccountNoLongerExistsException::new);
        Email nuevo = new Email(comando.newEmail());

        // Pedir el cambio al correo que ya se tiene no es un error, pero tampoco
        // hay nada que hacer: se termina en silencio, como el caso ocupado.
        if (cuenta.email().equals(nuevo)) {
            return;
        }

        Optional<User> ocupante = usuarios.buscarPorCorreo(nuevo);
        if (ocupante.isPresent()) {
            // Al titular si se le avisa: es su correo el que alguien intento usar.
            correo.enviarAvisoDeIntentoDeCambioAEsteCorreo(ocupante.get());
            return;
        }

        TokenGenerator.GeneratedToken generado = generadorDeTokens.generar();

        tokens.guardar(VerificationToken.paraCambioDeCorreo(
                cuenta.id(), nuevo, generado.hash(), ahora, VerificationToken.VIGENCIA_VERIFICACION_DE_CORREO));

        correo.enviarConfirmacionDeCorreoNuevo(cuenta, nuevo, generado.valorEnClaro());
    }
}
