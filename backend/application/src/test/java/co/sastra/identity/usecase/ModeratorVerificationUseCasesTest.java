package co.sastra.identity.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.sastra.identity.dto.ApproveVerificationCommand;
import co.sastra.identity.dto.RejectVerificationCommand;
import co.sastra.identity.dto.RevokeVerificationCommand;
import co.sastra.identity.exception.InvalidVerificationTransitionException;
import co.sastra.identity.exception.SelfReviewForbiddenException;
import co.sastra.identity.exception.VerificationNotFoundException;
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
import co.sastra.identity.model.RejectionReason;
import co.sastra.identity.model.Role;
import co.sastra.identity.model.SellerVerification;
import co.sastra.identity.model.SellerVerificationId;
import co.sastra.identity.model.User;
import co.sastra.identity.model.UserId;
import co.sastra.identity.model.UserLocale;
import co.sastra.identity.model.UserStatus;
import co.sastra.identity.model.VerificationAccess;
import co.sastra.identity.model.VerificationStatus;
import co.sastra.identity.port.out.MailSender;
import co.sastra.identity.port.out.SellerVerificationRepository;
import co.sastra.identity.port.out.UserRepository;
import co.sastra.identity.port.out.VerificationAccessLog;
import co.sastra.shared.file.FileKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** El camino del moderador: aprobar, rechazar y revocar, siempre con bitacora. */
class ModeratorVerificationUseCasesTest {

    private static final Instant AHORA = Instant.parse("2026-08-21T15:00:00Z");

    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);

    private static final LegalName TITULAR = new LegalName("Ana Maria Garcia");

    private final SellerVerificationRepository verificaciones = mock(SellerVerificationRepository.class);
    private final UserRepository usuarios = mock(UserRepository.class);
    private final VerificationAccessLog bitacora = mock(VerificationAccessLog.class);
    private final MailSender correo = mock(MailSender.class);

    private final UserId vendedor = UserId.nuevo();
    private final UserId moderador = UserId.nuevo();
    private final SellerVerificationId solicitud = SellerVerificationId.nuevo();

    private SellerVerification enRevision() {
        return SellerVerification.iniciar(solicitud, vendedor, AHORA)
                .conDocumento(
                        new IdentityDocument(
                                IdentityDocumentType.CC,
                                new IdentityDocumentNumber("1053812947"),
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
                        AHORA)
                .enviarARevision(AHORA);
    }

    private ApproveVerificationUseCase casoDeAprobar() {
        return new ApproveVerificationUseCase(verificaciones, usuarios, bitacora, correo, RELOJ);
    }

    // --- Aprobar. Criterio 8 -------------------------------------------------

    @Test
    void deberia_cumplir_el_criterio_8_otorgando_el_rol_de_vendedor() {
        when(verificaciones.buscarPorId(solicitud)).thenReturn(Optional.of(enRevision()));

        SellerVerification aprobada = casoDeAprobar().execute(new ApproveVerificationCommand(moderador, solicitud));

        assertThat(aprobada.status()).isEqualTo(VerificationStatus.VERIFIED);
        verify(usuarios).otorgarRol(vendedor, Role.SELLER, AHORA);
    }

    @Test
    void deberia_anotar_en_la_bitacora_quien_aprobo() {
        when(verificaciones.buscarPorId(solicitud)).thenReturn(Optional.of(enRevision()));

        casoDeAprobar().execute(new ApproveVerificationCommand(moderador, solicitud));

        verify(bitacora).registrar(solicitud, moderador, VerificationAccess.APPROVE, null, AHORA);
    }

    /**
     * Lo que el puerto promete: si no se puede escribir la bitacora, la operacion no
     * ocurre. Entre perder la aprobacion y perder el registro de quien la hizo, se
     * pierde la aprobacion.
     */
    @Test
    void deberia_propagar_el_fallo_de_la_bitacora() {
        when(verificaciones.buscarPorId(solicitud)).thenReturn(Optional.of(enRevision()));
        doThrow(new IllegalStateException("la bitacora no responde"))
                .when(bitacora)
                .registrar(any(), any(), any(), any(), any());

        ApproveVerificationUseCase caso = casoDeAprobar();

        assertThatThrownBy(() -> caso.execute(new ApproveVerificationCommand(moderador, solicitud)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deberia_negar_aprobar_lo_que_no_esta_en_revision() {
        when(verificaciones.buscarPorId(solicitud))
                .thenReturn(Optional.of(enRevision().rechazar(RejectionReason.ILLEGIBLE_PHOTOS, null, AHORA)));

        ApproveVerificationUseCase caso = casoDeAprobar();

        assertThatThrownBy(() -> caso.execute(new ApproveVerificationCommand(moderador, solicitud)))
                .isInstanceOf(InvalidVerificationTransitionException.class);

        verify(usuarios, never()).otorgarRol(any(), any(), any());
        verify(bitacora, never()).registrar(any(), any(), any(), any(), any());
    }

    @Test
    void deberia_fallar_si_la_verificacion_no_existe() {
        when(verificaciones.buscarPorId(solicitud)).thenReturn(Optional.empty());

        ApproveVerificationUseCase caso = casoDeAprobar();

        assertThatThrownBy(() -> caso.execute(new ApproveVerificationCommand(moderador, solicitud)))
                .isInstanceOf(VerificationNotFoundException.class);
    }

    // --- Rechazar. Criterio 7 ------------------------------------------------

    @Test
    void deberia_rechazar_con_motivo_y_nota_sin_tocar_los_roles() {
        when(verificaciones.buscarPorId(solicitud)).thenReturn(Optional.of(enRevision()));

        SellerVerification rechazada = new RejectVerificationUseCase(verificaciones, usuarios, bitacora, correo, RELOJ)
                .execute(new RejectVerificationCommand(
                        moderador, solicitud, RejectionReason.ILLEGIBLE_PHOTOS, "El reverso sale oscuro"));

        assertThat(rechazada.status()).isEqualTo(VerificationStatus.REJECTED);
        assertThat(rechazada.rejectionNote()).isEqualTo("El reverso sale oscuro");
        verify(usuarios, never()).otorgarRol(any(), any(), any());
        verify(usuarios, never()).revocarRol(any(), any());
    }

    /**
     * El motivo va a la bitacora ademas de a la solicitud: en la solicitud se pierde al
     * reintentar, y lo que permite revisar por que se rechazo tres veces a alguien es la
     * secuencia.
     */
    @Test
    void deberia_anotar_el_motivo_del_rechazo_en_la_bitacora() {
        when(verificaciones.buscarPorId(solicitud)).thenReturn(Optional.of(enRevision()));

        new RejectVerificationUseCase(verificaciones, usuarios, bitacora, correo, RELOJ)
                .execute(new RejectVerificationCommand(moderador, solicitud, RejectionReason.EXPIRED_DOCUMENT, null));

        verify(bitacora).registrar(solicitud, moderador, VerificationAccess.REJECT, "EXPIRED_DOCUMENT", AHORA);
    }

    // --- RN-060: nadie decide sobre su propia solicitud -----------------------

    /**
     * El caso que la regla ataca. Un moderador que se aprueba a si mismo se otorga el
     * sello que responde por una transaccion ante quien compra, y ese sello dejaria de
     * responder por nada.
     *
     * <p>Se comprueba tambien que <strong>no se toco nada</strong>: sin el corte antes
     * de guardar, una implementacion que lanzara al final habria dejado el rol otorgado
     * y la bitacora escrita, y solo la transaccion evitaria el desastre.
     */
    @Test
    void deberia_cumplir_RN_060_impidiendo_que_el_moderador_apruebe_su_propia_solicitud() {
        SellerVerification propia = SellerVerification.iniciar(solicitud, moderador, AHORA)
                .conDocumento(
                        new IdentityDocument(
                                IdentityDocumentType.CC,
                                new IdentityDocumentNumber("1053812947"),
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
                        AHORA)
                .enviarARevision(AHORA);
        when(verificaciones.buscarPorId(solicitud)).thenReturn(Optional.of(propia));

        assertThatThrownBy(() -> casoDeAprobar().execute(new ApproveVerificationCommand(moderador, solicitud)))
                .isInstanceOf(SelfReviewForbiddenException.class);

        verify(verificaciones, never()).guardar(any());
        verify(usuarios, never()).otorgarRol(any(), any(), any());
        verify(bitacora, never()).registrar(any(), any(), any(), any(), any());
    }

    /** RN-060 nombra las dos decisiones, no solo la que concede. */
    @Test
    void deberia_cumplir_RN_060_impidiendo_que_el_moderador_rechace_su_propia_solicitud() {
        SellerVerification propia = SellerVerification.iniciar(solicitud, moderador, AHORA)
                .conDocumento(
                        new IdentityDocument(
                                IdentityDocumentType.CC,
                                new IdentityDocumentNumber("1053812947"),
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
                        AHORA)
                .enviarARevision(AHORA);
        when(verificaciones.buscarPorId(solicitud)).thenReturn(Optional.of(propia));

        assertThatThrownBy(() -> new RejectVerificationUseCase(verificaciones, usuarios, bitacora, correo, RELOJ)
                        .execute(new RejectVerificationCommand(
                                moderador, solicitud, RejectionReason.ILLEGIBLE_PHOTOS, null)))
                .isInstanceOf(SelfReviewForbiddenException.class);

        verify(verificaciones, never()).guardar(any());
    }

    /**
     * La otra cara, y la que impide que la regla se implemente de mas: la solicitud de
     * otra persona se aprueba igual que siempre. Sin esta, un `if` invertido pasaria.
     */
    @Test
    void deberia_permitir_que_el_moderador_apruebe_la_solicitud_de_otra_persona() {
        when(verificaciones.buscarPorId(solicitud)).thenReturn(Optional.of(enRevision()));

        SellerVerification aprobada = casoDeAprobar().execute(new ApproveVerificationCommand(moderador, solicitud));

        assertThat(aprobada.status()).isEqualTo(VerificationStatus.VERIFIED);
    }

    // --- Revocar. RN-013 ----------------------------------------------------

    @Test
    void deberia_cumplir_RN_013_quitando_el_rol_al_revocar() {
        when(verificaciones.buscarPorId(solicitud))
                .thenReturn(Optional.of(enRevision().aprobar(AHORA)));

        SellerVerification revocada = new RevokeVerificationUseCase(verificaciones, usuarios, bitacora, correo, RELOJ)
                .execute(new RevokeVerificationCommand(
                        moderador, solicitud, RejectionReason.REQUIREMENTS_NOT_MET, null));

        assertThat(revocada.status()).isEqualTo(VerificationStatus.REVOKED);
        assertThat(revocada.status().esVerificado()).isFalse();
        verify(usuarios).revocarRol(vendedor, Role.SELLER);
        verify(bitacora).registrar(eq(solicitud), eq(moderador), eq(VerificationAccess.REVOKE), any(), eq(AHORA));
    }

    @Test
    void deberia_negar_revocar_lo_que_nunca_estuvo_verificado() {
        when(verificaciones.buscarPorId(solicitud)).thenReturn(Optional.of(enRevision()));

        RevokeVerificationUseCase caso =
                new RevokeVerificationUseCase(verificaciones, usuarios, bitacora, correo, RELOJ);
        RevokeVerificationCommand comando =
                new RevokeVerificationCommand(moderador, solicitud, RejectionReason.REQUIREMENTS_NOT_MET, null);

        assertThatThrownBy(() -> caso.execute(comando)).isInstanceOf(InvalidVerificationTransitionException.class);
        verify(usuarios, never()).revocarRol(any(), any());
    }

    // --- Criterio 10: cada cambio de estado avisa -----------------------------

    @Test
    void deberia_cumplir_el_criterio_10_avisando_al_aprobar() {
        when(verificaciones.buscarPorId(solicitud)).thenReturn(Optional.of(enRevision()));
        when(usuarios.buscarPorId(vendedor)).thenReturn(Optional.of(cuentaDelVendedor()));

        casoDeAprobar().execute(new ApproveVerificationCommand(moderador, solicitud));

        verify(correo).enviarAvisoDeVerificacionAprobada(any());
    }

    /**
     * El aviso lleva los intentos que quedan: en cero, el correo no invita a reintentar,
     * porque RN-014 no lo permite y mandar a alguien a una negativa es peor que callar.
     */
    @Test
    void deberia_cumplir_el_criterio_10_avisando_del_rechazo_con_los_intentos_que_quedan() {
        when(verificaciones.buscarPorId(solicitud)).thenReturn(Optional.of(enRevision()));
        when(usuarios.buscarPorId(vendedor)).thenReturn(Optional.of(cuentaDelVendedor()));

        new RejectVerificationUseCase(verificaciones, usuarios, bitacora, correo, RELOJ)
                .execute(new RejectVerificationCommand(
                        moderador, solicitud, RejectionReason.ILLEGIBLE_PHOTOS, "Sale oscuro"));

        // Un envio gastado de tres: quedan dos.
        verify(correo)
                .enviarAvisoDeVerificacionRechazada(
                        any(), eq(RejectionReason.ILLEGIBLE_PHOTOS), eq("Sale oscuro"), eq(2));
    }

    @Test
    void deberia_cumplir_RN_013_avisando_de_la_revocacion() {
        when(verificaciones.buscarPorId(solicitud))
                .thenReturn(Optional.of(enRevision().aprobar(AHORA)));
        when(usuarios.buscarPorId(vendedor)).thenReturn(Optional.of(cuentaDelVendedor()));

        new RevokeVerificationUseCase(verificaciones, usuarios, bitacora, correo, RELOJ)
                .execute(new RevokeVerificationCommand(
                        moderador, solicitud, RejectionReason.REQUIREMENTS_NOT_MET, null));

        verify(correo).enviarAvisoDeVerificacionRevocada(any(), eq(RejectionReason.REQUIREMENTS_NOT_MET), eq(null));
    }

    /**
     * Si la cuenta ya no existe no hay a quien escribir, y eso no puede tumbar la
     * decision: el moderador ya decidio y la bitacora ya lo anoto.
     */
    @Test
    void deberia_aprobar_igual_cuando_no_hay_a_quien_escribir() {
        when(verificaciones.buscarPorId(solicitud)).thenReturn(Optional.of(enRevision()));
        when(usuarios.buscarPorId(vendedor)).thenReturn(Optional.empty());

        SellerVerification aprobada = casoDeAprobar().execute(new ApproveVerificationCommand(moderador, solicitud));

        assertThat(aprobada.status().esVerificado()).isTrue();
        verify(correo, never()).enviarAvisoDeVerificacionAprobada(any());
    }

    private User cuentaDelVendedor() {
        return User.rehidratar(
                vendedor,
                new Email("ana@correo.co"),
                new DisplayName("Ana Maria"),
                new BirthDate(java.time.LocalDate.of(1990, 3, 4)),
                null,
                null,
                null,
                UserLocale.ES,
                UserStatus.ACTIVE,
                AHORA,
                java.util.Set.of(Role.BUYER),
                AHORA);
    }
}
