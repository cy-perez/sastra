package co.sastra.identity.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Las transiciones de RN-059, incluidas las que no existen. */
class VerificationStatusTest {

    @Test
    void deberia_cumplir_RN_059_con_las_transiciones_permitidas() {
        assertThat(VerificationStatus.NOT_STARTED.puedePasarA(VerificationStatus.IN_PROGRESS))
                .isTrue();
        assertThat(VerificationStatus.IN_PROGRESS.puedePasarA(VerificationStatus.PENDING_REVIEW))
                .isTrue();
        assertThat(VerificationStatus.PENDING_REVIEW.puedePasarA(VerificationStatus.VERIFIED))
                .isTrue();
        assertThat(VerificationStatus.PENDING_REVIEW.puedePasarA(VerificationStatus.REJECTED))
                .isTrue();
        assertThat(VerificationStatus.REJECTED.puedePasarA(VerificationStatus.IN_PROGRESS))
                .isTrue();
        assertThat(VerificationStatus.VERIFIED.puedePasarA(VerificationStatus.REVOKED))
                .isTrue();
        assertThat(VerificationStatus.REVOKED.puedePasarA(VerificationStatus.IN_PROGRESS))
                .isTrue();
    }

    /** Corregir un dato no cambia de estado, y sin esto guardar el avance fallaria. */
    @Test
    void deberia_permitir_quedarse_en_progreso() {
        assertThat(VerificationStatus.IN_PROGRESS.puedePasarA(VerificationStatus.IN_PROGRESS))
                .isTrue();
    }

    /**
     * Una solicitud enviada se revisa: no se puede retirar. Si se pudiera, habria
     * forma de que una cedula ya vista por el moderador desapareciera del expediente.
     */
    @Test
    void deberia_cumplir_RN_059_no_dejando_salir_de_revision_hacia_atras() {
        assertThat(VerificationStatus.PENDING_REVIEW.puedePasarA(VerificationStatus.IN_PROGRESS))
                .isFalse();
    }

    /** El estado inicial significa "sin intentos", y eso deja de ser cierto para siempre. */
    @Test
    void deberia_cumplir_RN_059_no_volviendo_nunca_al_estado_inicial() {
        for (VerificationStatus desde : VerificationStatus.values()) {
            assertThat(desde.puedePasarA(VerificationStatus.NOT_STARTED))
                    .as("de %s a NOT_STARTED", desde)
                    .isFalse();
        }
    }

    /** Rechazado no es revocado: no se puede saltar de uno al otro. */
    @Test
    void deberia_cumplir_RN_059_manteniendo_separados_el_rechazo_y_la_revocacion() {
        assertThat(VerificationStatus.REJECTED.puedePasarA(VerificationStatus.REVOKED))
                .isFalse();
        assertThat(VerificationStatus.REVOKED.puedePasarA(VerificationStatus.REJECTED))
                .isFalse();
    }

    /** Aprobar sin pasar por revision no existe. */
    @Test
    void deberia_cumplir_RN_059_exigiendo_pasar_por_revision_para_verificar() {
        assertThat(VerificationStatus.IN_PROGRESS.puedePasarA(VerificationStatus.VERIFIED))
                .isFalse();
        assertThat(VerificationStatus.NOT_STARTED.puedePasarA(VerificationStatus.VERIFIED))
                .isFalse();
        assertThat(VerificationStatus.REJECTED.puedePasarA(VerificationStatus.VERIFIED))
                .isFalse();
    }

    /**
     * La cuenta de transiciones esta fija a proposito: son las ocho de RN-059 y ni una
     * mas. Si alguien agrega una, esta prueba lo obliga a ir a la regla primero.
     */
    @Test
    void deberia_cumplir_RN_059_con_exactamente_ocho_transiciones() {
        Set<VerificationStatus> todos = EnumSet.allOf(VerificationStatus.class);

        long transiciones = todos.stream()
                .flatMap(desde -> todos.stream().filter(desde::puedePasarA))
                .count();

        assertThat(transiciones).isEqualTo(8);
    }

    @Test
    void deberia_reconocer_el_unico_estado_con_sello() {
        for (VerificationStatus estado : VerificationStatus.values()) {
            assertThat(estado.esVerificado()).as("%s", estado).isEqualTo(estado == VerificationStatus.VERIFIED);
        }
    }
}
