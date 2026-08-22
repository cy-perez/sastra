package co.sastra.identity.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.sastra.identity.dto.StartSellerVerificationCommand;
import co.sastra.identity.dto.SubmitBankAccountCommand;
import co.sastra.identity.dto.SubmitIdentityDocumentCommand;
import co.sastra.identity.dto.SubmitSelfieCommand;
import co.sastra.identity.dto.SubmitVerificationForReviewCommand;
import co.sastra.identity.exception.AccountHolderMismatchException;
import co.sastra.identity.exception.AccountNoLongerExistsException;
import co.sastra.identity.exception.DocumentAlreadyVerifiedException;
import co.sastra.identity.exception.EmailNotVerifiedException;
import co.sastra.identity.exception.InvalidVerificationTransitionException;
import co.sastra.identity.exception.UnderageException;
import co.sastra.identity.exception.UnknownFinancialInstitutionException;
import co.sastra.identity.model.BankAccount;
import co.sastra.identity.model.BankAccountNumber;
import co.sastra.identity.model.BankAccountType;
import co.sastra.identity.model.BankCode;
import co.sastra.identity.model.BirthDate;
import co.sastra.identity.model.DisplayName;
import co.sastra.identity.model.Email;
import co.sastra.identity.model.IdentityDocument;
import co.sastra.identity.model.IdentityDocumentNumber;
import co.sastra.identity.model.IdentityDocumentType;
import co.sastra.identity.model.LegalName;
import co.sastra.identity.model.Role;
import co.sastra.identity.model.SellerVerification;
import co.sastra.identity.model.SellerVerificationId;
import co.sastra.identity.model.User;
import co.sastra.identity.model.UserId;
import co.sastra.identity.model.UserLocale;
import co.sastra.identity.model.UserStatus;
import co.sastra.identity.model.VerificationStatus;
import co.sastra.identity.port.out.FinancialInstitutions;
import co.sastra.identity.port.out.MailSender;
import co.sastra.identity.port.out.SellerVerificationRepository;
import co.sastra.identity.port.out.UserRepository;
import co.sastra.shared.file.FileKey;
import co.sastra.shared.file.ImageContentType;
import co.sastra.shared.file.ImageDimensions;
import co.sastra.shared.file.ImagePolicy;
import co.sastra.shared.file.NormalizedImage;
import co.sastra.shared.port.out.ImageNormalizer;
import co.sastra.shared.port.out.RestrictedFileStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** El camino del vendedor de HU-002: iniciar, entregar los tres datos y enviar. */
class SellerVerificationUseCasesTest {

    private static final Instant AHORA = Instant.parse("2026-08-21T15:00:00Z");

    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);

    private static final LegalName TITULAR = new LegalName("Ana Maria Garcia");

    private static final String CEDULA = "1053812947";

    /** Un PNG minimo: lo que importa es que la politica reconozca la firma. */
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};

    private final UserRepository usuarios = mock(UserRepository.class);
    private final SellerVerificationRepository verificaciones = mock(SellerVerificationRepository.class);
    private final RestrictedFileStore almacen = mock(RestrictedFileStore.class);
    private final ImageNormalizer normalizador = mock(ImageNormalizer.class);
    private final FinancialInstitutions entidades = mock(FinancialInstitutions.class);
    private final MailSender correo = mock(MailSender.class);

    private final ImagePolicy politica = new ImagePolicy(8_000_000, new ImageDimensions(200, 200));

    private final UserId usuario = UserId.nuevo();

    private User cuenta(boolean correoVerificado, LocalDate nacimiento) {
        User nueva = User.registrar(
                usuario,
                new Email("ana@correo.co"),
                new DisplayName("Ana Maria"),
                new BirthDate(nacimiento),
                UserLocale.ES,
                LocalDate.of(2026, 8, 21),
                AHORA);

        return correoVerificado ? nueva.conCorreoVerificado(AHORA) : nueva;
    }

    private SellerVerification enProgreso() {
        return SellerVerification.iniciar(SellerVerificationId.nuevo(), usuario, AHORA);
    }

    private void normalizaCualquierImagen() {
        when(normalizador.normalizar(any(), any()))
                .thenReturn(new NormalizedImage(PNG, ImageContentType.PNG, new ImageDimensions(800, 600)));
    }

    // --- Iniciar. Criterio 1 -------------------------------------------------

    @Test
    void deberia_iniciar_la_verificacion_de_una_cuenta_habilitada() {
        User habilitada = cuenta(true, LocalDate.of(1990, 3, 4));
        when(usuarios.buscarPorId(usuario)).thenReturn(Optional.of(habilitada));
        when(verificaciones.buscarPorUsuario(usuario)).thenReturn(Optional.empty());

        SellerVerification creada = new StartSellerVerificationUseCase(usuarios, verificaciones, RELOJ)
                .execute(new StartSellerVerificationCommand(usuario));

        assertThat(creada.status()).isEqualTo(VerificationStatus.IN_PROGRESS);
        verify(verificaciones).guardar(creada);
    }

    /**
     * El caso borde de la historia: quien lo tenia empezado retoma donde iba. Crear
     * otra solicitud tiraria su avance.
     */
    @Test
    void deberia_devolver_la_solicitud_en_curso_sin_crear_otra() {
        SellerVerification existente = enProgreso();
        User habilitada = cuenta(true, LocalDate.of(1990, 3, 4));
        when(usuarios.buscarPorId(usuario)).thenReturn(Optional.of(habilitada));
        when(verificaciones.buscarPorUsuario(usuario)).thenReturn(Optional.of(existente));

        SellerVerification devuelta = new StartSellerVerificationUseCase(usuarios, verificaciones, RELOJ)
                .execute(new StartSellerVerificationCommand(usuario));

        assertThat(devuelta).isSameAs(existente);
        verify(verificaciones, never()).guardar(any());
    }

    @Test
    void deberia_cumplir_el_criterio_1_exigiendo_el_correo_verificado() {
        User sinVerificar = cuenta(false, LocalDate.of(1990, 3, 4));
        when(usuarios.buscarPorId(usuario)).thenReturn(Optional.of(sinVerificar));

        StartSellerVerificationUseCase caso = new StartSellerVerificationUseCase(usuarios, verificaciones, RELOJ);

        assertThatThrownBy(() -> caso.execute(new StartSellerVerificationCommand(usuario)))
                .isInstanceOf(EmailNotVerifiedException.class);
        verify(verificaciones, never()).guardar(any());
    }

    /**
     * RN-008 ya lo impide al registrarse, y se comprueba otra vez: el dominio se valida
     * a si mismo, asi que si manana el registro cambia esta puerta sigue cerrada.
     */
    @Test
    void deberia_cumplir_el_criterio_1_exigiendo_ser_mayor_de_edad() {
        // Se rehidrata en vez de registrar, y el motivo es la mejor noticia de esta
        // prueba: `User.registrar` aplica RN-008 y **no deja construir** una cuenta de
        // alguien menor de edad. Por ese camino la condicion es inalcanzable hoy, asi
        // que la unica forma de probarla es simular una fila ya guardada.
        //
        // La comprobacion existe igual: es defensa en profundidad para el dia que el
        // registro cambie, no codigo muerto.
        User menor = User.rehidratar(
                usuario,
                new Email("ana@correo.co"),
                new DisplayName("Ana Maria"),
                new BirthDate(LocalDate.of(2015, 3, 4)),
                null,
                null,
                null,
                UserLocale.ES,
                UserStatus.ACTIVE,
                AHORA,
                java.util.Set.of(Role.BUYER),
                AHORA);

        when(usuarios.buscarPorId(usuario)).thenReturn(Optional.of(menor));

        StartSellerVerificationUseCase caso = new StartSellerVerificationUseCase(usuarios, verificaciones, RELOJ);

        assertThatThrownBy(() -> caso.execute(new StartSellerVerificationCommand(usuario)))
                .isInstanceOf(UnderageException.class);
    }

    @Test
    void deberia_fallar_si_la_cuenta_ya_no_existe() {
        when(usuarios.buscarPorId(usuario)).thenReturn(Optional.empty());

        StartSellerVerificationUseCase caso = new StartSellerVerificationUseCase(usuarios, verificaciones, RELOJ);

        assertThatThrownBy(() -> caso.execute(new StartSellerVerificationCommand(usuario)))
                .isInstanceOf(AccountNoLongerExistsException.class);
    }

    // --- Documento. Criterios 2 y 5 ------------------------------------------

    private SubmitIdentityDocumentUseCase casoDeDocumento() {
        return new SubmitIdentityDocumentUseCase(verificaciones, almacen, normalizador, politica, RELOJ);
    }

    private SubmitIdentityDocumentCommand comandoDeDocumento() {
        return new SubmitIdentityDocumentCommand(usuario, IdentityDocumentType.CC, CEDULA, TITULAR.value(), PNG, PNG);
    }

    @Test
    void deberia_guardar_las_dos_caras_en_el_almacen_reservado() {
        when(verificaciones.buscarPorUsuario(usuario)).thenReturn(Optional.of(enProgreso()));
        when(verificaciones.existeOtraVerificadaConDocumento(anyString(), any()))
                .thenReturn(false);
        normalizaCualquierImagen();
        when(almacen.guardar(any(), any()))
                .thenReturn(new FileKey("documentos/frente.png"), new FileKey("documentos/reverso.png"));

        SellerVerification resultado = casoDeDocumento().execute(comandoDeDocumento());

        ArgumentCaptor<String> carpetas = ArgumentCaptor.forClass(String.class);
        verify(almacen, org.mockito.Mockito.times(2)).guardar(carpetas.capture(), any());

        assertThat(carpetas.getAllValues()).containsExactly("documentos", "documentos");
        assertThat(resultado.document()).isNotNull();
        assertThat(resultado.document().number().value()).isEqualTo(CEDULA);
    }

    /**
     * Se comprueba antes de guardar nada: subir dos imagenes de la cedula de alguien
     * para despues rechazarlas es guardar dos imagenes que no habia por que guardar.
     */
    @Test
    void deberia_cumplir_el_criterio_5_sin_llegar_a_guardar_las_imagenes() {
        when(verificaciones.buscarPorUsuario(usuario)).thenReturn(Optional.of(enProgreso()));
        when(verificaciones.existeOtraVerificadaConDocumento(CEDULA, usuario)).thenReturn(true);

        SubmitIdentityDocumentUseCase caso = casoDeDocumento();

        assertThatThrownBy(() -> caso.execute(comandoDeDocumento()))
                .isInstanceOf(DocumentAlreadyVerifiedException.class);
        verify(almacen, never()).guardar(any(), any());
    }

    @Test
    void deberia_borrar_las_imagenes_anteriores_al_reemplazar_el_documento() {
        FileKey frenteViejo = new FileKey("documentos/viejo-frente.png");
        FileKey reversoViejo = new FileKey("documentos/viejo-reverso.png");

        SellerVerification conDocumento = enProgreso()
                .conDocumento(
                        new IdentityDocument(
                                IdentityDocumentType.CC,
                                new IdentityDocumentNumber(CEDULA),
                                TITULAR,
                                frenteViejo,
                                reversoViejo),
                        AHORA);

        when(verificaciones.buscarPorUsuario(usuario)).thenReturn(Optional.of(conDocumento));
        when(verificaciones.existeOtraVerificadaConDocumento(anyString(), any()))
                .thenReturn(false);
        normalizaCualquierImagen();
        when(almacen.guardar(any(), any()))
                .thenReturn(new FileKey("documentos/nuevo-frente.png"), new FileKey("documentos/nuevo-reverso.png"));

        casoDeDocumento().execute(comandoDeDocumento());

        verify(almacen).borrar(frenteViejo);
        verify(almacen).borrar(reversoViejo);
    }

    @Test
    void deberia_fallar_al_entregar_el_documento_sin_haber_iniciado() {
        when(verificaciones.buscarPorUsuario(usuario)).thenReturn(Optional.empty());

        SubmitIdentityDocumentUseCase caso = casoDeDocumento();

        assertThatThrownBy(() -> caso.execute(comandoDeDocumento()))
                .isInstanceOf(InvalidVerificationTransitionException.class);
    }

    @Test
    void deberia_rechazar_una_imagen_que_no_es_de_un_tipo_aceptado() {
        when(verificaciones.buscarPorUsuario(usuario)).thenReturn(Optional.of(enProgreso()));
        when(verificaciones.existeOtraVerificadaConDocumento(anyString(), any()))
                .thenReturn(false);

        SubmitIdentityDocumentUseCase caso = casoDeDocumento();
        SubmitIdentityDocumentCommand conPdf = new SubmitIdentityDocumentCommand(
                usuario, IdentityDocumentType.CC, CEDULA, TITULAR.value(), "%PDF-1.7".getBytes(), PNG);

        assertThatThrownBy(() -> caso.execute(conPdf))
                .isInstanceOf(co.sastra.shared.file.UnsupportedImageTypeException.class);
        verify(almacen, never()).guardar(any(), any());
    }

    // --- Selfie. Criterio 3 --------------------------------------------------

    @Test
    void deberia_guardar_la_selfie_en_su_propia_carpeta_del_almacen_reservado() {
        when(verificaciones.buscarPorUsuario(usuario)).thenReturn(Optional.of(enProgreso()));
        normalizaCualquierImagen();
        when(almacen.guardar(any(), any())).thenReturn(new FileKey("selfies/abc.png"));

        SellerVerification resultado = new SubmitSelfieUseCase(verificaciones, almacen, normalizador, politica, RELOJ)
                .execute(new SubmitSelfieCommand(usuario, PNG));

        verify(almacen)
                .guardar("selfies", new NormalizedImage(PNG, ImageContentType.PNG, new ImageDimensions(800, 600)));
        assertThat(resultado.selfie()).isNotNull();
    }

    @Test
    void deberia_borrar_la_selfie_anterior_al_reemplazarla() {
        FileKey vieja = new FileKey("selfies/vieja.png");
        when(verificaciones.buscarPorUsuario(usuario))
                .thenReturn(Optional.of(enProgreso().conSelfie(vieja, AHORA)));
        normalizaCualquierImagen();
        when(almacen.guardar(any(), any())).thenReturn(new FileKey("selfies/nueva.png"));

        new SubmitSelfieUseCase(verificaciones, almacen, normalizador, politica, RELOJ)
                .execute(new SubmitSelfieCommand(usuario, PNG));

        verify(almacen).borrar(vieja);
    }

    // --- Cuenta bancaria. Criterio 4 y RN-012 --------------------------------

    private SubmitBankAccountCommand comandoDeCuenta(String titular) {
        return new SubmitBankAccountCommand(usuario, "bancolombia", BankAccountType.SAVINGS, "91500123456", titular);
    }

    @Test
    void deberia_registrar_la_cuenta_bancaria() {
        when(verificaciones.buscarPorUsuario(usuario)).thenReturn(Optional.of(enProgreso()));
        when(entidades.estaActiva(new BankCode("bancolombia"))).thenReturn(true);

        SellerVerification resultado = new SubmitBankAccountUseCase(verificaciones, entidades, RELOJ)
                .execute(comandoDeCuenta(TITULAR.value()));

        assertThat(resultado.bankAccount()).isNotNull();
        assertThat(resultado.bankAccount().number().ultimosCuatro()).isEqualTo("3456");
    }

    @Test
    void deberia_rechazar_una_entidad_que_no_esta_en_el_catalogo() {
        when(verificaciones.buscarPorUsuario(usuario)).thenReturn(Optional.of(enProgreso()));
        when(entidades.estaActiva(any())).thenReturn(false);

        SubmitBankAccountUseCase caso = new SubmitBankAccountUseCase(verificaciones, entidades, RELOJ);

        assertThatThrownBy(() -> caso.execute(comandoDeCuenta(TITULAR.value())))
                .isInstanceOf(UnknownFinancialInstitutionException.class);
        verify(verificaciones, never()).guardar(any());
    }

    @Test
    void deberia_cumplir_RN_012_rechazando_un_titular_distinto_del_documento() {
        SellerVerification conDocumento = enProgreso()
                .conDocumento(
                        new IdentityDocument(
                                IdentityDocumentType.CC,
                                new IdentityDocumentNumber(CEDULA),
                                TITULAR,
                                new FileKey("documentos/frente.png"),
                                new FileKey("documentos/reverso.png")),
                        AHORA);

        when(verificaciones.buscarPorUsuario(usuario)).thenReturn(Optional.of(conDocumento));
        when(entidades.estaActiva(any())).thenReturn(true);

        SubmitBankAccountUseCase caso = new SubmitBankAccountUseCase(verificaciones, entidades, RELOJ);

        assertThatThrownBy(() -> caso.execute(comandoDeCuenta("Pedro Ramirez")))
                .isInstanceOf(AccountHolderMismatchException.class);
        verify(verificaciones, never()).guardar(any());
    }

    // --- Enviar a revision. Criterio 6 ---------------------------------------

    private SellerVerification completa() {
        return enProgreso()
                .conDocumento(
                        new IdentityDocument(
                                IdentityDocumentType.CC,
                                new IdentityDocumentNumber(CEDULA),
                                TITULAR,
                                new FileKey("documentos/frente.png"),
                                new FileKey("documentos/reverso.png")),
                        AHORA)
                .conSelfie(new FileKey("selfies/abc.png"), AHORA)
                .conCuentaBancaria(
                        new BankAccount(
                                new BankCode("bancolombia"),
                                BankAccountType.SAVINGS,
                                new BankAccountNumber("91500123456"),
                                TITULAR),
                        AHORA);
    }

    @Test
    void deberia_enviar_a_revision_una_solicitud_completa() {
        when(verificaciones.buscarPorUsuario(usuario)).thenReturn(Optional.of(completa()));
        when(verificaciones.existeOtraVerificadaConDocumento(anyString(), any()))
                .thenReturn(false);

        SellerVerification enviada = new SubmitVerificationForReviewUseCase(verificaciones, usuarios, correo, RELOJ)
                .execute(new SubmitVerificationForReviewCommand(usuario));

        assertThat(enviada.status()).isEqualTo(VerificationStatus.PENDING_REVIEW);
        assertThat(enviada.attempts()).isEqualTo(1);
    }

    /**
     * Entre subir el documento y enviar pueden pasar dias, y en ese hueco otra cuenta
     * puede haber quedado verificada con el mismo documento.
     */
    @Test
    void deberia_volver_a_comprobar_el_criterio_5_al_enviar() {
        when(verificaciones.buscarPorUsuario(usuario)).thenReturn(Optional.of(completa()));
        when(verificaciones.existeOtraVerificadaConDocumento(CEDULA, usuario)).thenReturn(true);

        SubmitVerificationForReviewUseCase caso =
                new SubmitVerificationForReviewUseCase(verificaciones, usuarios, correo, RELOJ);

        assertThatThrownBy(() -> caso.execute(new SubmitVerificationForReviewCommand(usuario)))
                .isInstanceOf(DocumentAlreadyVerifiedException.class);
        verify(verificaciones, never()).guardar(any());
    }

    @Test
    void deberia_negar_el_envio_de_una_solicitud_incompleta() {
        when(verificaciones.buscarPorUsuario(usuario)).thenReturn(Optional.of(enProgreso()));

        SubmitVerificationForReviewUseCase caso =
                new SubmitVerificationForReviewUseCase(verificaciones, usuarios, correo, RELOJ);

        assertThatThrownBy(() -> caso.execute(new SubmitVerificationForReviewCommand(usuario)))
                .isInstanceOf(InvalidVerificationTransitionException.class);
    }
}
