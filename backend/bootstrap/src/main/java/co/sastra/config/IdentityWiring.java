package co.sastra.config;

import co.sastra.identity.port.out.BreachedPasswordChecker;
import co.sastra.identity.port.out.ConsentRepository;
import co.sastra.identity.port.out.LegalDocuments;
import co.sastra.identity.port.out.MailSender;
import co.sastra.identity.port.out.PasswordHasher;
import co.sastra.identity.port.out.TokenGenerator;
import co.sastra.identity.port.out.UserRepository;
import co.sastra.identity.port.out.VerificationTokenRepository;
import co.sastra.identity.usecase.RegisterUserUseCase;
import co.sastra.identity.usecase.ResendVerificationUseCase;
import co.sastra.identity.usecase.VerifyEmailUseCase;
import co.sastra.shared.config.AppProperties;
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
            Clock reloj) {
        return new VerifyEmailUseCase(usuarios, tokens, generadorDeTokens, reloj);
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
}
