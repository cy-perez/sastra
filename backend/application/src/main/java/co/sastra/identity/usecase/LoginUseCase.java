package co.sastra.identity.usecase;

import co.sastra.identity.dto.IssueSessionCommand;
import co.sastra.identity.dto.LoginCommand;
import co.sastra.identity.dto.SessionResult;
import co.sastra.identity.exception.InvalidCredentialsException;
import co.sastra.identity.model.Email;
import co.sastra.identity.model.RawPassword;
import co.sastra.identity.model.User;
import co.sastra.identity.model.UserCredentials;
import co.sastra.identity.port.out.CredentialsRepository;
import co.sastra.identity.port.out.LoginAttemptRecorder;
import co.sastra.identity.port.out.MailSender;
import co.sastra.identity.port.out.PasswordHasher;
import co.sastra.identity.port.out.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Ingreso con correo y contrasena. Criterios 10 a 13 de HU-001.
 *
 * <p><strong>Las dos reglas que dan forma a este codigo:</strong>
 *
 * <ol>
 *   <li>Criterio 11. Un correo que no existe y una contrasena equivocada
 *       responden lo mismo y tardan lo mismo. Por eso se compara la contrasena
 *       tambien cuando no hay cuenta: el puerto admite un hash nulo y compensa el
 *       tiempo. Sin eso, medir la respuesta seria suficiente para saber quien
 *       tiene cuenta en Sastra.
 *   <li>Criterio 12 con RN-006. El bloqueo solo se cuenta hacia afuera cuando la
 *       contrasena era correcta. A quien no la sabe se le responde el error
 *       generico, porque decirle "esta bloqueada" le confirma que la cuenta existe
 *       y ademas que alguien ha estado intentandolo.
 * </ol>
 *
 * <p><strong>Este caso de uso no abre transaccion, y es a proposito.</strong> El
 * contador de intentos se escribe y acto seguido se lanza la excepcion: dentro de
 * una transaccion, Spring la revertiria al ver la {@code RuntimeException} y el
 * contador volveria a cero en cada intento fallido. RN-006 quedaria escrita y
 * probada en el dominio, y sin efecto en produccion.
 *
 * <p>No hace falta ninguna otra garantia: el contador y el registro de auditoria son
 * escrituras independientes entre si, y la unica parte que si necesita ser atomica,
 * la emision de la sesion, la abre {@link IssueSessionUseCase} por su cuenta.
 */
public class LoginUseCase {

    private final UserRepository usuarios;
    private final CredentialsRepository credenciales;
    private final PasswordHasher hasher;
    private final LoginAttemptRecorder intentos;
    private final MailSender correo;
    private final IssueSessionUseCase abrirSesion;
    private final Clock reloj;

    public LoginUseCase(
            UserRepository usuarios,
            CredentialsRepository credenciales,
            PasswordHasher hasher,
            LoginAttemptRecorder intentos,
            MailSender correo,
            IssueSessionUseCase abrirSesion,
            Clock reloj) {
        this.usuarios = usuarios;
        this.credenciales = credenciales;
        this.hasher = hasher;
        this.intentos = intentos;
        this.correo = correo;
        this.abrirSesion = abrirSesion;
        this.reloj = reloj;
    }

    public SessionResult execute(LoginCommand comando) {
        Instant ahora = reloj.instant();

        Email correoNormalizado = new Email(comando.email());
        RawPassword contrasena = new RawPassword(comando.password());

        Optional<User> encontrado = usuarios.buscarPorCorreo(correoNormalizado);
        Optional<UserCredentials> guardadas =
                encontrado.flatMap(usuario -> credenciales.buscarPorUsuario(usuario.id()));

        // Se compara siempre, exista la cuenta o no. El hash nulo lo resuelve el
        // adaptador gastando el mismo tiempo (criterio 11).
        boolean coincide = hasher.coincide(
                contrasena, guardadas.map(UserCredentials::passwordHash).orElse(null));

        if (!coincide) {
            guardadas.ifPresent(credencial -> anotarFalloYAvisarSiSeBloquea(encontrado.get(), credencial, ahora));
            intentos.registrar(correoNormalizado, comando.ipHash(), false, ahora);
            throw new InvalidCredentialsException();
        }

        // Si la contrasena coincide, las dos cosas estan presentes.
        User usuario = encontrado.get();
        UserCredentials credencial = guardadas.get();

        if (credencial.estaBloqueada(ahora)) {
            intentos.registrar(correoNormalizado, comando.ipHash(), false, ahora);
            // Lanza AccountLockedException con el instante de desbloqueo, que el
            // borde traduce a Retry-After.
            credencial.verificarQueNoEstaBloqueada(ahora);
        }

        credenciales.actualizar(credencial.registrarExito());
        intentos.registrar(correoNormalizado, comando.ipHash(), true, ahora);

        // Criterio 13: una cuenta sin verificar entra igual. Lo que puede hacer
        // dentro no lo decide el ingreso, lo decide cada endpoint mirando el
        // token; aqui solo viaja el dato de si esta verificada.
        return abrirSesion.execute(new IssueSessionCommand(usuario, comando.userAgent(), comando.ipHash()));
    }

    /**
     * Suma el intento fallido y, si ese intento es el que bloquea la cuenta, avisa
     * al titular (criterio 12).
     *
     * <p>La comparacion es entre el antes y el despues, no un "si esta bloqueada":
     * asi el aviso sale una sola vez, en el intento que cierra la puerta, y no en
     * cada uno de los que vengan despues mientras siga cerrada.
     */
    private void anotarFalloYAvisarSiSeBloquea(User titular, UserCredentials antes, Instant ahora) {
        UserCredentials despues = antes.registrarFallo(ahora);
        credenciales.actualizar(despues);

        if (!antes.estaBloqueada(ahora) && despues.estaBloqueada(ahora)) {
            correo.enviarAvisoDeCuentaBloqueada(titular, requireLockedUntil(despues));
        }
    }

    /** El desbloqueo existe: {@code estaBloqueada} acaba de confirmarlo. */
    private static Instant requireLockedUntil(UserCredentials credencial) {
        @Nullable Instant hasta = credencial.lockedUntil();
        if (hasta == null) {
            throw new IllegalStateException("Una cuenta bloqueada sin instante de desbloqueo");
        }
        return hasta;
    }
}
