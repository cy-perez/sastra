package co.sendik.identity.usecase;

import co.sendik.identity.dto.RegisterUserCommand;
import co.sendik.identity.exception.BreachedPasswordException;
import co.sendik.identity.exception.ConsentRequiredException;
import co.sendik.identity.model.BirthDate;
import co.sendik.identity.model.Consent;
import co.sendik.identity.model.ConsentDocument;
import co.sendik.identity.model.DisplayName;
import co.sendik.identity.model.Email;
import co.sendik.identity.model.PasswordHash;
import co.sendik.identity.model.RawPassword;
import co.sendik.identity.model.TokenPurpose;
import co.sendik.identity.model.User;
import co.sendik.identity.model.UserId;
import co.sendik.identity.model.UserLocale;
import co.sendik.identity.model.VerificationToken;
import co.sendik.identity.port.out.BreachedPasswordChecker;
import co.sendik.identity.port.out.ConsentRepository;
import co.sendik.identity.port.out.LegalDocuments;
import co.sendik.identity.port.out.MailSender;
import co.sendik.identity.port.out.PasswordHasher;
import co.sendik.identity.port.out.TokenGenerator;
import co.sendik.identity.port.out.UserRepository;
import co.sendik.identity.port.out.VerificationTokenRepository;
import co.sendik.identity.service.PasswordPolicy;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro de una cuenta nueva. Criterios 1 a 6 de HU-001.
 *
 * <p><strong>El orden de las comprobaciones es parte de la seguridad, no una
 * preferencia de estilo.</strong> Todo lo que puede rechazar un registro se
 * evalua <em>antes</em> de mirar si el correo existe. Si la edad se comprobara
 * despues, registrar un correo ajeno con una fecha de menor de edad respondaria
 * 202 cuando la cuenta existe y 422 cuando no: el formulario se convertiria en
 * el detector de cuentas que el criterio 2 quiere evitar.
 */
public class RegisterUserUseCase {

    private static final System.Logger LOG = System.getLogger(RegisterUserUseCase.class.getName());

    private final UserRepository usuarios;
    private final ConsentRepository consentimientos;
    private final VerificationTokenRepository tokens;
    private final PasswordHasher hasher;
    private final BreachedPasswordChecker filtradas;
    private final TokenGenerator generadorDeTokens;
    private final MailSender correo;
    private final LegalDocuments documentosLegales;
    private final Clock reloj;

    public RegisterUserUseCase(
            UserRepository usuarios,
            ConsentRepository consentimientos,
            VerificationTokenRepository tokens,
            PasswordHasher hasher,
            BreachedPasswordChecker filtradas,
            TokenGenerator generadorDeTokens,
            MailSender correo,
            LegalDocuments documentosLegales,
            Clock reloj) {
        this.usuarios = usuarios;
        this.consentimientos = consentimientos;
        this.tokens = tokens;
        this.hasher = hasher;
        this.filtradas = filtradas;
        this.generadorDeTokens = generadorDeTokens;
        this.correo = correo;
        this.documentosLegales = documentosLegales;
        this.reloj = reloj;
    }

    @Transactional
    public void execute(RegisterUserCommand comando) {
        exigirLosDosConsentimientos(comando);

        Instant ahora = reloj.instant();
        LocalDate hoy = LocalDate.ofInstant(ahora, reloj.getZone());

        Email correoNormalizado = new Email(comando.email());
        RawPassword contrasena = new RawPassword(comando.password());

        verificarLaContrasena(contrasena);

        // Construir el usuario valida la edad (RN-008). Va antes de la consulta
        // por el mismo motivo que todo lo demas: para que el resultado no dependa
        // de si el correo existia.
        User candidato = User.registrar(
                UserId.nuevo(),
                correoNormalizado,
                new DisplayName(comando.displayName()),
                new BirthDate(comando.birthDate()),
                UserLocale.de(comando.locale()),
                hoy,
                ahora);

        // El hash es la operacion cara. Se hace siempre, tambien cuando la cuenta
        // ya existe, para que el tiempo de respuesta no delate cual de los dos
        // caminos se tomo.
        PasswordHash hash = hasher.hashear(contrasena);

        Optional<User> yaRegistrado = usuarios.buscarPorCorreo(correoNormalizado);
        if (yaRegistrado.isPresent()) {
            // Criterio 2: al titular se le avisa, a quien lo intento no se le
            // dice nada. La respuesta es la misma que la de un registro correcto.
            correo.enviarAvisoDeRegistroConCorreoExistente(yaRegistrado.get());
            return;
        }

        usuarios.crear(candidato, hash);
        guardarConsentimientos(candidato.id(), comando.ipHash(), ahora);
        emitirYEnviarVerificacion(candidato, ahora);
    }

    private static void exigirLosDosConsentimientos(RegisterUserCommand comando) {
        if (!comando.acceptsTerms() || !comando.acceptsPrivacy()) {
            throw new ConsentRequiredException();
        }
    }

    private void guardarConsentimientos(UserId usuario, String ipHash, Instant ahora) {
        consentimientos.guardarTodos(List.of(
                Consent.otorgar(
                        usuario,
                        ConsentDocument.TERMS,
                        documentosLegales.versionVigente(ConsentDocument.TERMS),
                        ahora,
                        ipHash),
                Consent.otorgar(
                        usuario,
                        ConsentDocument.PRIVACY,
                        documentosLegales.versionVigente(ConsentDocument.PRIVACY),
                        ahora,
                        ipHash)));
    }

    private void emitirYEnviarVerificacion(User usuario, Instant ahora) {
        TokenGenerator.GeneratedToken generado = generadorDeTokens.generar();

        tokens.guardar(VerificationToken.emitir(
                usuario.id(),
                TokenPurpose.EMAIL_VERIFICATION,
                generado.hash(),
                ahora,
                VerificationToken.VIGENCIA_VERIFICACION_DE_CORREO));

        correo.enviarVerificacionDeCorreo(usuario, generado.valorEnClaro());
    }

    /**
     * RN-005 completa. El largo se comprueba siempre y sin salir a la red; la
     * lista de filtradas exige un tercero y por eso puede no estar disponible.
     */
    private void verificarLaContrasena(RawPassword contrasena) {
        PasswordPolicy.verificar(contrasena);

        BreachedPasswordChecker.Resultado resultado = filtradas.verificar(contrasena);
        if (resultado == BreachedPasswordChecker.Resultado.FILTRADA) {
            throw new BreachedPasswordException();
        }
        if (resultado == BreachedPasswordChecker.Resultado.NO_SE_PUDO_COMPROBAR) {
            // Fallo abierto (ADR-0013): bloquear un registro legitimo porque un
            // tercero se cayo convierte su disponibilidad en la nuestra. El
            // minimo de diez caracteres ya se aplico arriba, sin depender de nadie.
            LOG.log(
                    System.Logger.Level.WARNING,
                    "No se pudo comprobar si la contrasena esta filtrada; se acepta el registro (ADR-0013)");
        }
    }
}
