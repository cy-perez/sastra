package co.sastra.identity.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.sastra.identity.exception.DocumentAlreadyVerifiedException;
import co.sastra.identity.exception.EmailNotVerifiedException;
import co.sastra.identity.exception.UnknownFinancialInstitutionException;
import co.sastra.identity.exception.VerificationNotFoundException;
import co.sastra.shared.error.ErrorCode;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * El identificador de una solicitud, las acciones de la bitacora y los codigos de
 * error de HU-002.
 *
 * <p>Los codigos se comprueban aqui porque son contrato: el frontend traduce por
 * codigo, y cambiar uno sin cambiar la traduccion deja a la persona con un mensaje
 * generico donde deberia haber una instruccion.
 */
class VerificationIdentityAndAuditTest {

    // --- Identificador -------------------------------------------------------

    @Test
    void deberia_generar_un_identificador_ordenable_por_tiempo() {
        // v7 como toda clave primaria (ADR-0015): la excepcion es la clave de archivo.
        assertThat(SellerVerificationId.nuevo().value().version()).isEqualTo(7);
    }

    @Test
    void deberia_leer_un_identificador_desde_texto() {
        UUID uuid = UUID.randomUUID();

        assertThat(SellerVerificationId.de(uuid.toString()).value()).isEqualTo(uuid);
        assertThat(SellerVerificationId.de(uuid.toString())).hasToString(uuid.toString());
    }

    @Test
    void deberia_rechazar_un_identificador_que_no_es_un_uuid() {
        assertThatThrownBy(() -> SellerVerificationId.de("no-soy-un-uuid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UUID");
        assertThatThrownBy(() -> SellerVerificationId.de(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void deberia_exigir_un_valor() {
        assertThatThrownBy(() -> new SellerVerificationId(null)).isInstanceOf(NullPointerException.class);
    }

    // --- Bitacora ------------------------------------------------------------

    /**
     * Los valores tienen que ser exactamente los que admite el {@code CHECK} de
     * {@code verification_access_log} en V8. Si alguien agrega uno aqui sin migracion,
     * el insert falla en produccion y no antes; esta prueba al menos obliga a mirar la
     * lista.
     */
    @Test
    void deberia_declarar_las_siete_acciones_que_la_migracion_conoce() {
        assertThat(EnumSet.allOf(VerificationAccess.class))
                .containsExactlyInAnyOrder(
                        VerificationAccess.VIEW_DOCUMENT_FRONT,
                        VerificationAccess.VIEW_DOCUMENT_BACK,
                        VerificationAccess.VIEW_SELFIE,
                        VerificationAccess.VIEW_BANK_ACCOUNT,
                        VerificationAccess.APPROVE,
                        VerificationAccess.REJECT,
                        VerificationAccess.REVOKE);
    }

    @Test
    void deberia_distinguir_mirar_de_decidir() {
        long miradas = EnumSet.allOf(VerificationAccess.class).stream()
                .filter(accion -> accion.name().startsWith("VIEW_"))
                .count();

        assertThat(miradas).isEqualTo(4);
    }

    @Test
    void deberia_leer_una_accion_desde_su_nombre() {
        assertThat(VerificationAccess.valueOf("APPROVE")).isEqualTo(VerificationAccess.APPROVE);
    }

    // --- Codigos de error ----------------------------------------------------

    @Test
    void deberia_llevar_el_codigo_del_documento_ya_verificado() {
        assertThat(new DocumentAlreadyVerifiedException().code()).isEqualTo(ErrorCode.SELLER_DOCUMENT_ALREADY_VERIFIED);
    }

    @Test
    void deberia_llevar_el_codigo_del_correo_sin_verificar() {
        assertThat(new EmailNotVerifiedException().code()).isEqualTo(ErrorCode.SELLER_EMAIL_NOT_VERIFIED);
    }

    @Test
    void deberia_llevar_el_codigo_de_la_entidad_desconocida() {
        assertThat(new UnknownFinancialInstitutionException("banco-inventado").code())
                .isEqualTo(ErrorCode.SELLER_UNKNOWN_INSTITUTION);
    }

    /**
     * Reutiliza {@code COMMON_NOT_FOUND} a proposito: un codigo propio de verificacion
     * permitiria distinguir «no existe» de «existe y no es tuya», que es contar algo.
     */
    @Test
    void deberia_llevar_el_codigo_generico_cuando_la_verificacion_no_existe() {
        assertThat(new VerificationNotFoundException(SellerVerificationId.nuevo()).code())
                .isEqualTo(ErrorCode.COMMON_NOT_FOUND);
    }

    /** Ninguna excepcion de estos datos lleva el dato en el mensaje. */
    @Test
    void deberia_mantener_los_numeros_fuera_de_los_mensajes() {
        assertThat(new DocumentAlreadyVerifiedException().getMessage()).doesNotContain("1053812947");
        assertThat(new co.sastra.identity.exception.AccountHolderMismatchException().getMessage())
                .doesNotContain("Ana", "Pedro");
    }
}
