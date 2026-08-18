package co.sastra.identity.usecase;

import co.sastra.identity.dto.ResetPasswordCommand;
import co.sastra.identity.exception.BreachedPasswordException;
import co.sastra.identity.exception.ResetTokenExpiredException;
import co.sastra.identity.exception.ResetTokenInvalidException;
import co.sastra.identity.model.PasswordHash;
import co.sastra.identity.model.RawPassword;
import co.sastra.identity.model.TokenPurpose;
import co.sastra.identity.model.User;
import co.sastra.identity.model.UserCredentials;
import co.sastra.identity.model.VerificationToken;
import co.sastra.identity.port.out.BreachedPasswordChecker;
import co.sastra.identity.port.out.CredentialsRepository;
import co.sastra.identity.port.out.MailSender;
import co.sastra.identity.port.out.PasswordHasher;
import co.sastra.identity.port.out.RefreshTokenRepository;
import co.sastra.identity.port.out.TokenGenerator;
import co.sastra.identity.port.out.UserRepository;
import co.sastra.identity.port.out.VerificationTokenRepository;
import co.sastra.identity.service.PasswordPolicy;
import java.time.Clock;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pone una contrasena nueva con el enlace del correo. Criterios 18 y 20.
 *
 * <p><strong>Va en una transaccion, al reves que el ingreso y el refresco.</strong>
 * Aquellos escriben y despues lanzan, asi que una transaccion les revertiria lo
 * escrito. Aqui es lo contrario: consumir el token, cambiar la contrasena y cortar
 * las sesiones son tres escrituras que solo valen juntas. Si se guardara la
 * contrasena nueva y fallara la revocacion, quedaria una sesion viva de quien tal
 * vez provoco el cambio; si se consumiera el token y fallara el cambio, la persona
 * se quedaria sin enlace y sin contrasena nueva.
 *
 * <p>El aviso al titular va al final a proposito. Sale por el puerto de correo, que
 * es asincrono y no participa de la transaccion, asi que ponerlo antes podria
 * anunciar un cambio que despues se revierte.
 */
public class ResetPasswordUseCase {

    private static final System.Logger LOG = System.getLogger(ResetPasswordUseCase.class.getName());

    private final UserRepository usuarios;
    private final VerificationTokenRepository tokens;
    private final CredentialsRepository credenciales;
    private final RefreshTokenRepository refrescos;
    private final TokenGenerator generadorDeTokens;
    private final PasswordHasher hasher;
    private final BreachedPasswordChecker filtradas;
    private final MailSender correo;
    private final Clock reloj;

    public ResetPasswordUseCase(
            UserRepository usuarios,
            VerificationTokenRepository tokens,
            CredentialsRepository credenciales,
            RefreshTokenRepository refrescos,
            TokenGenerator generadorDeTokens,
            PasswordHasher hasher,
            BreachedPasswordChecker filtradas,
            MailSender correo,
            Clock reloj) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.credenciales = credenciales;
        this.refrescos = refrescos;
        this.generadorDeTokens = generadorDeTokens;
        this.hasher = hasher;
        this.filtradas = filtradas;
        this.correo = correo;
        this.reloj = reloj;
    }

    @Transactional
    public void execute(ResetPasswordCommand comando) {
        Instant ahora = reloj.instant();

        RawPassword nueva = new RawPassword(comando.newPassword());
        VerificationToken token = tokenUtilizable(comando.token(), ahora);

        // La contrasena se comprueba despues de validar el token y antes de
        // consumirlo: asi una contrasena rechazada no gasta el enlace, y quien se
        // equivoca de contrasena puede reintentar con el mismo correo.
        verificarLaContrasena(nueva);

        User usuario = usuarios.buscarPorId(token.userId()).orElseThrow(ResetTokenInvalidException::new);
        UserCredentials actuales =
                credenciales.buscarPorUsuario(usuario.id()).orElseThrow(ResetTokenInvalidException::new);

        PasswordHash hash = hasher.hashear(nueva);
        // cambiarContrasena y no actualizar: el segundo escribe solo el contador de
        // intentos y deja el hash intacto a proposito, para que un intento fallido
        // no pueda reescribir la credencial.
        credenciales.cambiarContrasena(actuales.conNuevaContrasena(hash, ahora));

        tokens.actualizar(token.marcarUsado(ahora));

        // Criterio 20. Se corta todo, tambien la sesion desde la que se pidio el
        // cambio: la persona vuelve a entrar con la contrasena nueva, que es la
        // unica forma de comprobar que la recuerda.
        refrescos.revocarTodasDe(usuario.id(), ahora);

        correo.enviarAvisoDeContrasenaCambiada(usuario);
    }

    /**
     * El token tiene que existir, ser de restablecimiento, no estar usado y no
     * haber caducado.
     *
     * <p>Que sea del proposito correcto no es formalismo: sin esa comprobacion, un
     * enlace de verificacion de correo serviria para cambiar la contrasena, y ese
     * dura 24 horas en vez de 30 minutos.
     */
    private VerificationToken tokenUtilizable(String enClaro, Instant ahora) {
        String hash = generadorDeTokens.hashearRecibido(enClaro);
        VerificationToken token = tokens.buscarPorHash(hash).orElseThrow(ResetTokenInvalidException::new);

        if (token.purpose() != TokenPurpose.PASSWORD_RESET || token.yaSeUso()) {
            throw new ResetTokenInvalidException();
        }
        if (token.estaCaducado(ahora)) {
            throw new ResetTokenExpiredException();
        }
        return token;
    }

    /**
     * RN-005 completa, la misma que el registro. Recuperar el acceso no es motivo
     * para admitir una contrasena peor: si lo fuera, bastaria pedir el enlace para
     * saltarse la regla.
     */
    private void verificarLaContrasena(RawPassword contrasena) {
        PasswordPolicy.verificar(contrasena);

        BreachedPasswordChecker.Resultado resultado = filtradas.verificar(contrasena);
        if (resultado == BreachedPasswordChecker.Resultado.FILTRADA) {
            throw new BreachedPasswordException();
        }
        if (resultado == BreachedPasswordChecker.Resultado.NO_SE_PUDO_COMPROBAR) {
            // Fallo abierto (ADR-0013), y aqui pesa mas que en el registro: dejar a
            // alguien sin poder recuperar su cuenta porque un tercero se cayo es
            // peor que aceptar una contrasena que quiza aparezca en una lista.
            LOG.log(
                    System.Logger.Level.WARNING,
                    "No se pudo comprobar si la contrasena esta filtrada; se acepta el cambio (ADR-0013)");
        }
    }
}
