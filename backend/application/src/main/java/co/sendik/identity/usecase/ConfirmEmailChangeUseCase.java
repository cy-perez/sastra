package co.sendik.identity.usecase;

import co.sendik.identity.exception.AccountNoLongerExistsException;
import co.sendik.identity.exception.EmailAlreadyTakenException;
import co.sendik.identity.exception.VerificationTokenExpiredException;
import co.sendik.identity.exception.VerificationTokenInvalidException;
import co.sendik.identity.model.Email;
import co.sendik.identity.model.TokenPurpose;
import co.sendik.identity.model.User;
import co.sendik.identity.model.VerificationToken;
import co.sendik.identity.port.out.MailSender;
import co.sendik.identity.port.out.TokenGenerator;
import co.sendik.identity.port.out.UserRepository;
import co.sendik.identity.port.out.VerificationTokenRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;

/**
 * Confirma el correo nuevo y lo reemplaza. Criterio 21, segunda mitad.
 *
 * <p>Es el momento en que el cambio ocurre de verdad, y hasta aqui la persona
 * conservo su correo anterior intacto.
 *
 * <p><strong>RN-001 se comprueba otra vez.</strong> Al pedir el cambio la
 * direccion estaba libre, pero entre aquello y esto pudo registrarse alguien:
 * media hora, un dia. Sin esta segunda comprobacion, confirmar reventaria contra
 * la restriccion de unicidad de la base con un error que no dice nada.
 *
 * <p>El aviso va al correo <strong>anterior</strong>, no al nuevo. No lo pide la
 * historia y es lo que evita el peor caso: quien te robe la sesion cambia tu
 * correo y te saca de tu cuenta en silencio. Al buzon nuevo no hace falta
 * avisarle, que acaba de abrir el enlace.
 */
public class ConfirmEmailChangeUseCase {

    private final UserRepository usuarios;
    private final VerificationTokenRepository tokens;
    private final TokenGenerator generadorDeTokens;
    private final MailSender correo;
    private final Clock reloj;

    public ConfirmEmailChangeUseCase(
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
    public void execute(String tokenEnClaro) {
        Instant ahora = reloj.instant();

        VerificationToken token = tokens.buscarPorHash(generadorDeTokens.hashearRecibido(tokenEnClaro))
                .orElseThrow(VerificationTokenInvalidException::new);

        if (token.purpose() != TokenPurpose.EMAIL_CHANGE || token.yaSeUso()) {
            throw new VerificationTokenInvalidException();
        }
        if (token.estaCaducado(ahora)) {
            throw new VerificationTokenExpiredException();
        }

        Email nuevo = token.newEmail();
        User cuenta = usuarios.buscarPorId(token.userId()).orElseThrow(AccountNoLongerExistsException::new);

        // Segunda comprobacion de RN-001: entre pedir y confirmar cabe un registro.
        if (usuarios.buscarPorCorreo(nuevo).isPresent()) {
            throw new EmailAlreadyTakenException();
        }

        Email anterior = cuenta.email();

        usuarios.actualizarCorreo(cuenta.conCorreoCambiado(nuevo, ahora));
        tokens.actualizar(token.marcarUsado(ahora));

        // Al anterior, que es quien tiene que enterarse si esto no lo pidio.
        correo.enviarAvisoDeCorreoCambiado(cuenta, anterior);
    }
}
