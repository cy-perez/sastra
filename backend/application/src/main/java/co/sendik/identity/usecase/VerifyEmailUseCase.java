package co.sendik.identity.usecase;

import co.sendik.identity.dto.IssueSessionCommand;
import co.sendik.identity.dto.SessionResult;
import co.sendik.identity.dto.VerifyEmailCommand;
import co.sendik.identity.dto.VerifyEmailResult;
import co.sendik.identity.exception.VerificationTokenInvalidException;
import co.sendik.identity.model.Role;
import co.sendik.identity.model.TokenPurpose;
import co.sendik.identity.model.User;
import co.sendik.identity.model.VerificationToken;
import co.sendik.identity.port.out.ConfiguredModerators;
import co.sendik.identity.port.out.TokenGenerator;
import co.sendik.identity.port.out.UserRepository;
import co.sendik.identity.port.out.VerificationTokenRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;

/**
 * Activacion de la cuenta con el enlace del correo. Criterios 7 a 9 de HU-001.
 *
 * <p>El criterio 9 pide dos cosas: que la cuenta quede activa y que la persona
 * entre directamente. La sesion se emite en la misma transaccion que consume el
 * token, porque el enlace es de un solo uso: si se emitiera en una segunda llamada
 * y esa llamada se perdiera, el token ya estaria gastado y no habria forma de
 * recuperar la sesion.
 */
public class VerifyEmailUseCase {

    private final UserRepository usuarios;
    private final VerificationTokenRepository tokens;
    private final TokenGenerator generadorDeTokens;
    private final IssueSessionUseCase abrirSesion;
    private final ConfiguredModerators moderadoresConfigurados;
    private final Clock reloj;

    public VerifyEmailUseCase(
            UserRepository usuarios,
            VerificationTokenRepository tokens,
            TokenGenerator generadorDeTokens,
            IssueSessionUseCase abrirSesion,
            ConfiguredModerators moderadoresConfigurados,
            Clock reloj) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.generadorDeTokens = generadorDeTokens;
        this.abrirSesion = abrirSesion;
        this.moderadoresConfigurados = moderadoresConfigurados;
        this.reloj = reloj;
    }

    @Transactional
    public VerifyEmailResult execute(VerifyEmailCommand comando) {
        Instant ahora = reloj.instant();

        // El valor que llega en el enlace se hashea antes de consultar: la base
        // guarda el hash y nunca ve el original.
        String hash = generadorDeTokens.hashearRecibido(comando.token());

        VerificationToken token = tokens.buscarPorHash(hash).orElseThrow(VerificationTokenInvalidException::new);

        if (token.purpose() != TokenPurpose.EMAIL_VERIFICATION) {
            // Un token de restablecimiento no activa una cuenta. Mezclar los
            // propositos convertiria un enlace en otro.
            throw new VerificationTokenInvalidException();
        }

        token.verificarUtilizable(ahora);

        User usuario = usuarios.buscarPorId(token.userId()).orElseThrow(VerificationTokenInvalidException::new);
        boolean yaEstabaVerificado = usuario.tieneElCorreoVerificado();

        User verificado = usuario.conCorreoVerificado(ahora);
        usuarios.actualizar(verificado);
        otorgarModeracionSiEstaConfigurada(verificado, ahora);
        tokens.actualizar(token.marcarUsado(ahora));

        // Con el usuario ya verificado, no con el de antes: el token de acceso lleva
        // el estado del correo y tiene que nacer diciendo que la cuenta esta activa.
        SessionResult sesion =
                abrirSesion.execute(new IssueSessionCommand(verificado, comando.userAgent(), comando.ipHash()));

        return new VerifyEmailResult(sesion, yaEstabaVerificado);
    }

    /**
     * HU-006: los correos declarados moderadores reciben el rol <strong>aqui</strong>, al
     * verificar, y no al registrarse.
     *
     * <p>La diferencia es de seguridad y no de orden. Registrarse solo demuestra que
     * alguien sabe escribir un correo; verificarlo demuestra que controla el buzon. Con
     * la concesion en el registro, cualquiera que se adelantara a la persona legitima
     * —el correo de moderacion de un marketplace es adivinable— se llevaba el rol sin
     * tocar ese buzon, y entraba con el, porque el criterio 13 de HU-001 deja entrar sin
     * verificar. Desde ahi se leen las cedulas y las selfies de todos los vendedores
     * pendientes. Estuvo asi y lo encontro la auditoria de la propia historia.
     *
     * <p>Sigue sirviendo para lo que motivo la variable: se configura el correo antes de
     * que la persona exista, y el rol le llega en cuanto abre su enlace.
     *
     * <p>Con la lista vacia —lo normal— esto no hace nada.
     */
    private void otorgarModeracionSiEstaConfigurada(User verificado, Instant ahora) {
        if (moderadoresConfigurados.incluye(verificado.email())) {
            usuarios.otorgarRol(verificado.id(), Role.MODERATOR, ahora);
        }
    }
}
