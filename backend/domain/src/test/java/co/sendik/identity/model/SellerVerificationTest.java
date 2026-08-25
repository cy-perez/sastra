package co.sendik.identity.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.sendik.identity.exception.AccountHolderMismatchException;
import co.sendik.identity.exception.InvalidVerificationTransitionException;
import co.sendik.identity.exception.VerificationAttemptsExhaustedException;
import co.sendik.shared.file.FileKey;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** El recorrido de una verificacion y las reglas que lo gobiernan. */
class SellerVerificationTest {

    private static final Instant AHORA = Instant.parse("2026-08-21T15:00:00Z");

    private static final LegalName TITULAR = new LegalName("Ana Garcia Lopez");

    private static SellerVerification iniciada() {
        return SellerVerification.iniciar(SellerVerificationId.nuevo(), UserId.nuevo(), AHORA);
    }

    private static IdentityDocument documento(LegalName titular) {
        return new IdentityDocument(
                IdentityDocumentType.CC,
                new IdentityDocumentNumber("1234567"),
                titular,
                new FileKey("documentos/frente.jpg"),
                new FileKey("documentos/reverso.jpg"));
    }

    private static BankAccount cuenta(LegalName titular) {
        return new BankAccount(
                new BankCode("bancolombia"), BankAccountType.SAVINGS, new BankAccountNumber("1234567890"), titular);
    }

    private static SellerVerification completa() {
        return iniciada()
                .conDocumento(documento(TITULAR), AHORA)
                .conSelfie(new FileKey("selfies/abc.jpg"), AHORA)
                .conCuentaBancaria(cuenta(TITULAR), AHORA);
    }

    @Test
    void deberia_nacer_en_progreso_y_sin_intentos() {
        SellerVerification verificacion = iniciada();

        assertThat(verificacion.status()).isEqualTo(VerificationStatus.IN_PROGRESS);
        assertThat(verificacion.attempts()).isZero();
        assertThat(verificacion.estaCompleta()).isFalse();
    }

    /**
     * El caso borde de HU-002: se sale a la mitad y se retoma donde iba. Los tres datos
     * llegan por separado y en cualquier orden.
     */
    @Test
    void deberia_guardar_el_avance_dato_por_dato_en_cualquier_orden() {
        SellerVerification verificacion = iniciada()
                .conCuentaBancaria(cuenta(TITULAR), AHORA)
                .conSelfie(new FileKey("selfies/abc.jpg"), AHORA)
                .conDocumento(documento(TITULAR), AHORA);

        assertThat(verificacion.estaCompleta()).isTrue();
        assertThat(verificacion.status()).isEqualTo(VerificationStatus.IN_PROGRESS);
    }

    @Test
    void deberia_enviar_a_revision_cuando_esta_completa() {
        SellerVerification enviada = completa().enviarARevision(AHORA);

        assertThat(enviada.status()).isEqualTo(VerificationStatus.PENDING_REVIEW);
        assertThat(enviada.attempts()).isEqualTo(1);
    }

    @Test
    void deberia_negar_el_envio_si_falta_alguno_de_los_tres_datos() {
        SellerVerification sinCuenta =
                iniciada().conDocumento(documento(TITULAR), AHORA).conSelfie(new FileKey("selfies/abc.jpg"), AHORA);

        assertThatThrownBy(() -> sinCuenta.enviarARevision(AHORA))
                .isInstanceOf(InvalidVerificationTransitionException.class);
    }

    // --- RN-012 --------------------------------------------------------------

    @Test
    void deberia_cumplir_RN_012_rechazando_una_cuenta_de_otro_titular() {
        SellerVerification conDocumento = iniciada().conDocumento(documento(TITULAR), AHORA);

        assertThatThrownBy(() -> conDocumento.conCuentaBancaria(cuenta(new LegalName("Pedro Ramirez")), AHORA))
                .isInstanceOf(AccountHolderMismatchException.class);
    }

    /** Cambiar el documento puede romper una coincidencia que ya estaba bien. */
    @Test
    void deberia_cumplir_RN_012_al_cambiar_el_documento_despues_de_la_cuenta() {
        SellerVerification conCuenta = iniciada().conCuentaBancaria(cuenta(TITULAR), AHORA);

        assertThatThrownBy(() -> conCuenta.conDocumento(documento(new LegalName("Pedro Ramirez")), AHORA))
                .isInstanceOf(AccountHolderMismatchException.class);
    }

    /**
     * Si la cuenta se registra antes del documento no hay con que comparar, y no se
     * puede rechazar por eso: la comparacion espera al segundo dato.
     */
    @Test
    void deberia_dejar_registrar_la_cuenta_cuando_todavia_no_hay_documento() {
        assertThatCode(() -> iniciada().conCuentaBancaria(cuenta(new LegalName("Pedro Ramirez")), AHORA))
                .doesNotThrowAnyException();
    }

    // --- Revision -------------------------------------------------------------

    @Test
    void deberia_aprobar_desde_revision() {
        SellerVerification aprobada = completa().enviarARevision(AHORA).aprobar(AHORA);

        assertThat(aprobada.status().esVerificado()).isTrue();
    }

    @Test
    void deberia_rechazar_con_motivo_y_nota() {
        SellerVerification rechazada = completa()
                .enviarARevision(AHORA)
                .rechazar(RejectionReason.ILLEGIBLE_PHOTOS, "  El reverso sale oscuro  ", AHORA);

        assertThat(rechazada.status()).isEqualTo(VerificationStatus.REJECTED);
        assertThat(rechazada.rejectionReason()).isEqualTo(RejectionReason.ILLEGIBLE_PHOTOS);
        assertThat(rechazada.rejectionNote()).isEqualTo("El reverso sale oscuro");
    }

    /** Una nota en blanco es no haber escrito nota. */
    @Test
    void deberia_tratar_una_nota_en_blanco_como_ausente() {
        SellerVerification rechazada =
                completa().enviarARevision(AHORA).rechazar(RejectionReason.EXPIRED_DOCUMENT, "   ", AHORA);

        assertThat(rechazada.rejectionNote()).isNull();
    }

    @Test
    void deberia_negar_aprobar_lo_que_no_esta_en_revision() {
        SellerVerification enProgreso = completa();

        assertThatThrownBy(() -> enProgreso.aprobar(AHORA)).isInstanceOf(InvalidVerificationTransitionException.class);
    }

    /** Una solicitud enviada no se toca mientras alguien la mira. */
    @Test
    void deberia_negar_editar_lo_que_esta_en_revision() {
        SellerVerification enRevision = completa().enviarARevision(AHORA);

        assertThatThrownBy(() -> enRevision.conSelfie(new FileKey("selfies/otra.jpg"), AHORA))
                .isInstanceOf(InvalidVerificationTransitionException.class);
    }

    // --- RN-013 --------------------------------------------------------------

    @Test
    void deberia_cumplir_RN_013_revocando_a_quien_ya_tenia_el_sello() {
        SellerVerification revocada = completa()
                .enviarARevision(AHORA)
                .aprobar(AHORA)
                .revocar(RejectionReason.REQUIREMENTS_NOT_MET, null, AHORA);

        assertThat(revocada.status()).isEqualTo(VerificationStatus.REVOKED);
        assertThat(revocada.status().esVerificado()).isFalse();
    }

    @Test
    void deberia_negar_revocar_lo_que_nunca_estuvo_verificado() {
        SellerVerification rechazada =
                completa().enviarARevision(AHORA).rechazar(RejectionReason.ILLEGIBLE_PHOTOS, null, AHORA);

        assertThatThrownBy(() -> rechazada.revocar(RejectionReason.REQUIREMENTS_NOT_MET, null, AHORA))
                .isInstanceOf(InvalidVerificationTransitionException.class);
    }

    // --- RN-014 --------------------------------------------------------------

    @Test
    void deberia_conservar_los_datos_y_limpiar_el_motivo_al_reintentar() {
        SellerVerification reintentada = completa()
                .enviarARevision(AHORA)
                .rechazar(RejectionReason.ILLEGIBLE_PHOTOS, "Sale oscuro", AHORA)
                .reintentar(AHORA);

        assertThat(reintentada.status()).isEqualTo(VerificationStatus.IN_PROGRESS);
        assertThat(reintentada.estaCompleta()).isTrue();
        assertThat(reintentada.rejectionReason()).isNull();
        assertThat(reintentada.rejectionNote()).isNull();
        // El intento gastado no se devuelve: RN-014 cuenta envios, no correcciones.
        assertThat(reintentada.attempts()).isEqualTo(1);
    }

    @Test
    void deberia_cumplir_RN_014_permitiendo_tres_intentos() {
        SellerVerification verificacion = completa();

        for (int intento = 1; intento <= SellerVerification.MAXIMO_INTENTOS; intento++) {
            verificacion = verificacion.enviarARevision(AHORA);
            assertThat(verificacion.attempts()).isEqualTo(intento);

            verificacion = verificacion.rechazar(RejectionReason.ILLEGIBLE_PHOTOS, null, AHORA);
            if (intento < SellerVerification.MAXIMO_INTENTOS) {
                verificacion = verificacion.reintentar(AHORA);
            }
        }

        assertThat(verificacion.agotoLosIntentos()).isTrue();
    }

    /**
     * El cuarto no se deja reintentar solo. Se niega al reintentar y no al enviar:
     * dejar que alguien corrija todo el formulario para negarle el envio al final es la
     * misma negativa con el trabajo perdido en medio.
     */
    @Test
    void deberia_cumplir_RN_014_negando_el_cuarto_intento() {
        SellerVerification agotada = completa();

        for (int intento = 1; intento <= SellerVerification.MAXIMO_INTENTOS; intento++) {
            agotada = agotada.enviarARevision(AHORA).rechazar(RejectionReason.ILLEGIBLE_PHOTOS, null, AHORA);
            if (intento < SellerVerification.MAXIMO_INTENTOS) {
                agotada = agotada.reintentar(AHORA);
            }
        }

        SellerVerification sinIntentos = agotada;
        assertThatThrownBy(() -> sinIntentos.reintentar(AHORA))
                .isInstanceOf(VerificationAttemptsExhaustedException.class);
    }

    /**
     * El hueco que aparecio al escribir la pantalla: desde REJECTED, editar un dato mueve
     * el estado a IN_PROGRESS por RN-059, asi que editar ES reintentar y el limite de
     * RN-014 tiene que aplicar tambien ahi. Sin esta comprobacion, alguien sin intentos
     * rellenaba el formulario entero para que se lo negaran al enviar.
     */
    @Test
    void deberia_cumplir_RN_014_tambien_al_corregir_un_dato_sin_intentos() {
        SellerVerification agotada = completa();

        for (int intento = 1; intento <= SellerVerification.MAXIMO_INTENTOS; intento++) {
            agotada = agotada.enviarARevision(AHORA).rechazar(RejectionReason.ILLEGIBLE_PHOTOS, null, AHORA);
            if (intento < SellerVerification.MAXIMO_INTENTOS) {
                agotada = agotada.reintentar(AHORA);
            }
        }

        SellerVerification sinIntentos = agotada;

        assertThatThrownBy(() -> sinIntentos.conSelfie(new FileKey("selfies/otra.jpg"), AHORA))
                .isInstanceOf(VerificationAttemptsExhaustedException.class);
        assertThatThrownBy(() -> sinIntentos.conDocumento(documento(TITULAR), AHORA))
                .isInstanceOf(VerificationAttemptsExhaustedException.class);
        assertThatThrownBy(() -> sinIntentos.conCuentaBancaria(cuenta(TITULAR), AHORA))
                .isInstanceOf(VerificationAttemptsExhaustedException.class);
    }

    /** Con intentos disponibles, corregir desde un rechazo si funciona y vuelve a curso. */
    @Test
    void deberia_dejar_corregir_desde_un_rechazo_cuando_quedan_intentos() {
        SellerVerification rechazada =
                completa().enviarARevision(AHORA).rechazar(RejectionReason.ILLEGIBLE_PHOTOS, null, AHORA);

        SellerVerification corregida = rechazada.conSelfie(new FileKey("selfies/otra.jpg"), AHORA);

        assertThat(corregida.status()).isEqualTo(VerificationStatus.IN_PROGRESS);
        assertThat(corregida.attempts()).isEqualTo(1);
    }

    // --- Inmutabilidad --------------------------------------------------------

    @Test
    void deberia_devolver_una_instancia_nueva_en_cada_paso() {
        SellerVerification inicial = iniciada();
        SellerVerification conDocumento = inicial.conDocumento(documento(TITULAR), AHORA);

        assertThat(conDocumento).isNotSameAs(inicial);
        assertThat(inicial.document()).isNull();
        assertThat(conDocumento.document()).isNotNull();
    }

    @Test
    void deberia_conservar_la_fecha_de_creacion_y_mover_la_de_actualizacion() {
        Instant despues = AHORA.plusSeconds(60);

        SellerVerification avanzada = iniciada().conSelfie(new FileKey("selfies/abc.jpg"), despues);

        assertThat(avanzada.createdAt()).isEqualTo(AHORA);
        assertThat(avanzada.updatedAt()).isEqualTo(despues);
    }
}
