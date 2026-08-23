package co.sastra.config;

import co.sastra.identity.config.SessionProperties;
import co.sastra.identity.port.out.AccessTokenIssuer;
import co.sastra.identity.port.out.BreachedPasswordChecker;
import co.sastra.identity.port.out.ConfiguredModerators;
import co.sastra.identity.port.out.ConsentRepository;
import co.sastra.identity.port.out.CredentialsRepository;
import co.sastra.identity.port.out.FinancialInstitutions;
import co.sastra.identity.port.out.LegalDocuments;
import co.sastra.identity.port.out.LoginAttemptRecorder;
import co.sastra.identity.port.out.MailSender;
import co.sastra.identity.port.out.PasswordHasher;
import co.sastra.identity.port.out.RefreshTokenRepository;
import co.sastra.identity.port.out.SellerVerificationRepository;
import co.sastra.identity.port.out.TokenGenerator;
import co.sastra.identity.port.out.UserRepository;
import co.sastra.identity.port.out.VerificationAccessLog;
import co.sastra.identity.port.out.VerificationTokenRepository;
import co.sastra.identity.usecase.ApproveVerificationUseCase;
import co.sastra.identity.usecase.CloseAccountUseCase;
import co.sastra.identity.usecase.ConfirmEmailChangeUseCase;
import co.sastra.identity.usecase.ExportUserDataUseCase;
import co.sastra.identity.usecase.ForgotPasswordUseCase;
import co.sastra.identity.usecase.GrantConfiguredModeratorsUseCase;
import co.sastra.identity.usecase.IssueSessionUseCase;
import co.sastra.identity.usecase.ListFinancialInstitutionsUseCase;
import co.sastra.identity.usecase.ListPendingVerificationsUseCase;
import co.sastra.identity.usecase.ListSessionsUseCase;
import co.sastra.identity.usecase.LoginUseCase;
import co.sastra.identity.usecase.LogoutUseCase;
import co.sastra.identity.usecase.ReadProfileUseCase;
import co.sastra.identity.usecase.ReadSellerVerificationUseCase;
import co.sastra.identity.usecase.RefreshSessionUseCase;
import co.sastra.identity.usecase.RegisterUserUseCase;
import co.sastra.identity.usecase.RejectVerificationUseCase;
import co.sastra.identity.usecase.RemoveAvatarUseCase;
import co.sastra.identity.usecase.RequestEmailChangeUseCase;
import co.sastra.identity.usecase.RequestEmailVerificationUseCase;
import co.sastra.identity.usecase.ResendVerificationUseCase;
import co.sastra.identity.usecase.ResetPasswordUseCase;
import co.sastra.identity.usecase.RevokeSessionUseCase;
import co.sastra.identity.usecase.RevokeVerificationUseCase;
import co.sastra.identity.usecase.StartSellerVerificationUseCase;
import co.sastra.identity.usecase.SubmitBankAccountUseCase;
import co.sastra.identity.usecase.SubmitIdentityDocumentUseCase;
import co.sastra.identity.usecase.SubmitSelfieUseCase;
import co.sastra.identity.usecase.SubmitVerificationForReviewUseCase;
import co.sastra.identity.usecase.UpdateAvatarUseCase;
import co.sastra.identity.usecase.UpdateProfileUseCase;
import co.sastra.identity.usecase.VerifyEmailUseCase;
import co.sastra.identity.usecase.ViewVerificationImageUseCase;
import co.sastra.shared.config.AppProperties;
import co.sastra.shared.file.ImageDimensions;
import co.sastra.shared.file.ImagePolicy;
import co.sastra.shared.file.StorageProperties;
import co.sastra.shared.port.out.ImageNormalizer;
import co.sastra.shared.port.out.PublicFileStore;
import co.sastra.shared.port.out.RestrictedFileStore;
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
            ConfiguredModerators moderadoresConfigurados,
            Clock reloj) {
        return new VerifyEmailUseCase(usuarios, tokens, generadorDeTokens, abrirSesion, moderadoresConfigurados, reloj);
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

    @Bean
    ListSessionsUseCase listSessionsUseCase(RefreshTokenRepository refrescos, Clock reloj) {
        return new ListSessionsUseCase(refrescos, reloj);
    }

    @Bean
    RevokeSessionUseCase revokeSessionUseCase(RefreshTokenRepository refrescos, Clock reloj) {
        return new RevokeSessionUseCase(refrescos, reloj);
    }

    @Bean
    ExportUserDataUseCase exportUserDataUseCase(
            UserRepository usuarios, ConsentRepository consentimientos, RefreshTokenRepository refrescos, Clock reloj) {
        return new ExportUserDataUseCase(usuarios, consentimientos, refrescos, reloj);
    }

    @Bean
    CloseAccountUseCase closeAccountUseCase(
            UserRepository usuarios,
            RefreshTokenRepository refrescos,
            MailSender correo,
            PublicFileStore almacen,
            Clock reloj) {
        return new CloseAccountUseCase(usuarios, refrescos, correo, almacen, reloj);
    }

    /**
     * La politica de la foto de perfil.
     *
     * <p>Se llama asi y no "politica de imagenes" a proposito: las tomas de producto
     * tendran la suya, con el minimo de RN-019, y son numeros distintos. Un unico
     * bean compartido habria acabado aplicando 900x1200 al avatar, que rechaza casi
     * cualquier foto que alguien tenga a mano.
     */
    @Bean
    ImagePolicy politicaDeAvatar(StorageProperties almacenamiento) {
        return new ImagePolicy(
                almacenamiento.maxImageBytes(),
                new ImageDimensions(almacenamiento.avatarMinWidth(), almacenamiento.avatarMinHeight()));
    }

    @Bean
    UpdateAvatarUseCase updateAvatarUseCase(
            UserRepository usuarios, PublicFileStore almacen, ImageNormalizer normalizador, ImagePolicy politica) {
        return new UpdateAvatarUseCase(usuarios, almacen, normalizador, politica);
    }

    @Bean
    RemoveAvatarUseCase removeAvatarUseCase(UserRepository usuarios, PublicFileStore almacen) {
        return new RemoveAvatarUseCase(usuarios, almacen);
    }

    @Bean
    ReadProfileUseCase readProfileUseCase(UserRepository usuarios) {
        return new ReadProfileUseCase(usuarios);
    }

    // --- Verificacion de vendedor. HU-002 rebanada C -------------------------
    //
    // Los tres casos de uso que suben imagenes reciben el almacen RESERVADO y no el
    // publico, y ahi esta la garantia de RN-046: no es que se acuerden de usar el
    // correcto, es que el otro no lo tienen.
    //
    // La politica de imagen es la misma del avatar por ahora. Nadie ha decidido un
    // minimo de pixeles para la foto de una cedula, y ponerle uno inventado
    // rechazaria documentos legibles; que la foto se pueda leer lo decide el
    // moderador, y para eso existe el motivo de rechazo ILLEGIBLE_PHOTOS.

    @Bean
    StartSellerVerificationUseCase startSellerVerificationUseCase(
            UserRepository usuarios, SellerVerificationRepository verificaciones, Clock reloj) {
        return new StartSellerVerificationUseCase(usuarios, verificaciones, reloj);
    }

    @Bean
    SubmitIdentityDocumentUseCase submitIdentityDocumentUseCase(
            SellerVerificationRepository verificaciones,
            RestrictedFileStore almacen,
            ImageNormalizer normalizador,
            ImagePolicy politica,
            Clock reloj) {
        return new SubmitIdentityDocumentUseCase(verificaciones, almacen, normalizador, politica, reloj);
    }

    @Bean
    SubmitSelfieUseCase submitSelfieUseCase(
            SellerVerificationRepository verificaciones,
            RestrictedFileStore almacen,
            ImageNormalizer normalizador,
            ImagePolicy politica,
            Clock reloj) {
        return new SubmitSelfieUseCase(verificaciones, almacen, normalizador, politica, reloj);
    }

    @Bean
    SubmitBankAccountUseCase submitBankAccountUseCase(
            SellerVerificationRepository verificaciones, FinancialInstitutions entidades, Clock reloj) {
        return new SubmitBankAccountUseCase(verificaciones, entidades, reloj);
    }

    @Bean
    ReadSellerVerificationUseCase readSellerVerificationUseCase(SellerVerificationRepository verificaciones) {
        return new ReadSellerVerificationUseCase(verificaciones);
    }

    @Bean
    SubmitVerificationForReviewUseCase submitVerificationForReviewUseCase(
            SellerVerificationRepository verificaciones, UserRepository usuarios, MailSender correo, Clock reloj) {
        return new SubmitVerificationForReviewUseCase(verificaciones, usuarios, correo, reloj);
    }

    // El camino del moderador. Los tres reciben la bitacora, y los dos que mueven el
    // sello reciben tambien el repositorio de cuentas: aprobar otorga el rol SELLER y
    // revocar lo quita, en la misma transaccion que el cambio de estado.

    @Bean
    ListFinancialInstitutionsUseCase listFinancialInstitutionsUseCase(FinancialInstitutions entidades) {
        return new ListFinancialInstitutionsUseCase(entidades);
    }

    @Bean
    ListPendingVerificationsUseCase listPendingVerificationsUseCase(SellerVerificationRepository verificaciones) {
        return new ListPendingVerificationsUseCase(verificaciones);
    }

    @Bean
    ViewVerificationImageUseCase viewVerificationImageUseCase(
            SellerVerificationRepository verificaciones,
            RestrictedFileStore almacen,
            VerificationAccessLog bitacora,
            Clock reloj) {
        return new ViewVerificationImageUseCase(verificaciones, almacen, bitacora, reloj);
    }

    @Bean
    ApproveVerificationUseCase approveVerificationUseCase(
            SellerVerificationRepository verificaciones,
            UserRepository usuarios,
            VerificationAccessLog bitacora,
            MailSender correo,
            Clock reloj) {
        return new ApproveVerificationUseCase(verificaciones, usuarios, bitacora, correo, reloj);
    }

    @Bean
    RejectVerificationUseCase rejectVerificationUseCase(
            SellerVerificationRepository verificaciones,
            UserRepository usuarios,
            VerificationAccessLog bitacora,
            MailSender correo,
            Clock reloj) {
        return new RejectVerificationUseCase(verificaciones, usuarios, bitacora, correo, reloj);
    }

    @Bean
    RevokeVerificationUseCase revokeVerificationUseCase(
            SellerVerificationRepository verificaciones,
            UserRepository usuarios,
            VerificationAccessLog bitacora,
            MailSender correo,
            Clock reloj) {
        return new RevokeVerificationUseCase(verificaciones, usuarios, bitacora, correo, reloj);
    }

    /** HU-006: quien arranca siendo moderador. Con la lista vacia no hace nada. */
    @Bean
    GrantConfiguredModeratorsUseCase grantConfiguredModeratorsUseCase(UserRepository usuarios, Clock reloj) {
        return new GrantConfiguredModeratorsUseCase(usuarios, reloj);
    }

    @Bean
    UpdateProfileUseCase updateProfileUseCase(UserRepository usuarios) {
        return new UpdateProfileUseCase(usuarios);
    }

    @Bean
    RequestEmailChangeUseCase requestEmailChangeUseCase(
            UserRepository usuarios,
            VerificationTokenRepository tokens,
            TokenGenerator generadorDeTokens,
            MailSender correo,
            Clock reloj) {
        return new RequestEmailChangeUseCase(usuarios, tokens, generadorDeTokens, correo, reloj);
    }

    @Bean
    ConfirmEmailChangeUseCase confirmEmailChangeUseCase(
            UserRepository usuarios,
            VerificationTokenRepository tokens,
            TokenGenerator generadorDeTokens,
            MailSender correo,
            Clock reloj) {
        return new ConfirmEmailChangeUseCase(usuarios, tokens, generadorDeTokens, correo, reloj);
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
