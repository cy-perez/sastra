package co.sastra.config;

import co.sastra.identity.config.SessionProperties;
import co.sastra.identity.port.out.AccessTokenIssuer;
import co.sastra.identity.port.out.BreachedPasswordChecker;
import co.sastra.identity.port.out.ConsentRepository;
import co.sastra.identity.port.out.CredentialsRepository;
import co.sastra.identity.port.out.LegalDocuments;
import co.sastra.identity.port.out.LoginAttemptRecorder;
import co.sastra.identity.port.out.MailSender;
import co.sastra.identity.port.out.PasswordHasher;
import co.sastra.identity.port.out.RefreshTokenRepository;
import co.sastra.identity.port.out.TokenGenerator;
import co.sastra.identity.port.out.UserRepository;
import co.sastra.identity.port.out.VerificationTokenRepository;
import co.sastra.identity.usecase.ForgotPasswordUseCase;
import co.sastra.identity.usecase.IssueSessionUseCase;
import co.sastra.identity.usecase.LoginUseCase;
import co.sastra.identity.usecase.LogoutUseCase;
import co.sastra.identity.usecase.RefreshSessionUseCase;
import co.sastra.identity.usecase.RegisterUserUseCase;
import co.sastra.identity.usecase.RequestEmailVerificationUseCase;
import co.sastra.identity.usecase.ResendVerificationUseCase;
import co.sastra.identity.usecase.ResetPasswordUseCase;
import co.sastra.identity.usecase.VerifyEmailUseCase;
import co.sastra.shared.config.AppProperties;
import co.sastra.shared.rest.RefreshCookies;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cableado de los casos de uso de identidad.
 *
 * <p>Los casos de uso no llevan {@code @Service} ni ninguna otra anotacion: el
 * modulo {@code application} solo puede ver {@code spring-tx}, y una prueba de
 * arquitectura falla si aparece cualquier otra cosa de Spring. Por eso se
 * registran aqui, en {@code bootstrap}, que es el modulo del cableado.
 *
 * <p>Tiene una ventaja que no es solo formal: los casos de uso se construyen con
 * {@code new} en sus pruebas, sin contexto de Spring y sin simular un
 * contenedor.
 */
@Configuration
public class IdentityWiring {

    /**
     * Reloj del sistema en la zona de operacion, no en UTC.
     *
     * <p>RN-008 compara fechas, no instantes: con UTC alguien en Colombia
     * cumpliria 18 anos cinco horas antes de que aqui sea su cumpleanos.
     */
    @Bean
    Clock reloj(AppProperties app) {
        return Clock.system(app.timeZone());
    }

    @Bean
    RegisterUserUseCase registerUserUseCase(
            UserRepository usuarios,
            ConsentRepository consentimientos,
            VerificationTokenRepository tokens,
            PasswordHasher hasher,
            BreachedPasswordChecker filtradas,
            TokenGenerator generadorDeTokens,
            MailSender correo,
            LegalDocuments documentosLegales,
            Clock reloj) {
        return new RegisterUserUseCase(
                usuarios,
                consentimientos,
                tokens,
                hasher,
                filtradas,
                generadorDeTokens,
                correo,
                documentosLegales,
                reloj);
    }

    @Bean
    VerifyEmailUseCase verifyEmailUseCase(
            UserRepository usuarios,
            VerificationTokenRepository tokens,
            TokenGenerator generadorDeTokens,
            IssueSessionUseCase abrirSesion,
            Clock reloj) {
        return new VerifyEmailUseCase(usuarios, tokens, generadorDeTokens, abrirSesion, reloj);
    }

    @Bean
    ResendVerificationUseCase resendVerificationUseCase(
            UserRepository usuarios,
            VerificationTokenRepository tokens,
            TokenGenerator generadorDeTokens,
            MailSender correo,
            Clock reloj) {
        return new ResendVerificationUseCase(usuarios, tokens, generadorDeTokens, correo, reloj);
    }

    @Bean
    RequestEmailVerificationUseCase requestEmailVerificationUseCase(
            UserRepository usuarios,
            VerificationTokenRepository tokens,
            TokenGenerator generadorDeTokens,
            MailSender correo,
            Clock reloj) {
        return new RequestEmailVerificationUseCase(usuarios, tokens, generadorDeTokens, correo, reloj);
    }

    /**
     * La vigencia del refresco llega como {@link java.time.Duration} y no como el
     * objeto de configuracion completo: un caso de uso no tiene por que conocer el
     * formato en que se configura el sistema, solo el plazo que debe aplicar.
     */
    @Bean
    IssueSessionUseCase issueSessionUseCase(
            RefreshTokenRepository refrescos,
            AccessTokenIssuer accesos,
            TokenGenerator generadorDeTokens,
            SessionProperties sesion,
            Clock reloj) {
        return new IssueSessionUseCase(refrescos, accesos, generadorDeTokens, sesion.refreshTtl(), reloj);
    }

    @Bean
    LoginUseCase loginUseCase(
            UserRepository usuarios,
            CredentialsRepository credenciales,
            PasswordHasher hasher,
            LoginAttemptRecorder intentos,
            MailSender correo,
            IssueSessionUseCase abrirSesion,
            Clock reloj) {
        return new LoginUseCase(usuarios, credenciales, hasher, intentos, correo, abrirSesion, reloj);
    }

    @Bean
    RefreshSessionUseCase refreshSessionUseCase(
            RefreshTokenRepository refrescos,
            UserRepository usuarios,
            AccessTokenIssuer accesos,
            TokenGenerator generadorDeTokens,
            MailSender correo,
            SessionProperties sesion,
            Clock reloj) {
        return new RefreshSessionUseCase(
                refrescos,
                usuarios,
                accesos,
                generadorDeTokens,
                correo,
                sesion.refreshTtl(),
                sesion.refreshGrace(),
                reloj);
    }

    @Bean
    LogoutUseCase logoutUseCase(RefreshTokenRepository refrescos, TokenGenerator generadorDeTokens, Clock reloj) {
        return new LogoutUseCase(refrescos, generadorDeTokens, reloj);
    }

    @Bean
    ForgotPasswordUseCase forgotPasswordUseCase(
            UserRepository usuarios,
            VerificationTokenRepository tokens,
            TokenGenerator generadorDeTokens,
            MailSender correo,
            Clock reloj) {
        return new ForgotPasswordUseCase(usuarios, tokens, generadorDeTokens, correo, reloj);
    }

    @Bean
    ResetPasswordUseCase resetPasswordUseCase(
            UserRepository usuarios,
            VerificationTokenRepository tokens,
            CredentialsRepository credenciales,
            RefreshTokenRepository refrescos,
            TokenGenerator generadorDeTokens,
            PasswordHasher hasher,
            BreachedPasswordChecker filtradas,
            MailSender correo,
            Clock reloj) {
        return new ResetPasswordUseCase(
                usuarios, tokens, credenciales, refrescos, generadorDeTokens, hasher, filtradas, correo, reloj);
    }

    /**
     * La cookie del token de refresco.
     *
     * <p>Vive aqui por lo mismo que el origen de CORS: necesita a la vez la
     * configuracion tipada, que es de {@code infrastructure}, y el tipo que consume
     * el controlador, que es de {@code presentation}. Ningun otro modulo ve las dos
     * cosas.
     */
    @Bean
    RefreshCookies refreshCookies(SessionProperties sesion) {
        return new RefreshCookies(
                sesion.cookie().name(), sesion.cookie().path(), sesion.cookie().secure(), sesion.refreshTtl());
    }
}
