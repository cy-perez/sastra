package co.sendik.catalog.model;

import static co.sendik.catalog.model.CatalogoDePrueba.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ModerationEventTest {

    @Nested
    class LoQueGuarda {

        @Test
        void deberia_guardar_que_paso_con_que_motivo_y_cuando() {
            ModerationEvent evento =
                    new ModerationEvent(ModerationAction.REJECTED, ListingRejectionReason.PHOTOS_UNUSABLE, AHORA);

            assertThat(evento.action()).isEqualTo(ModerationAction.REJECTED);
            assertThat(evento.reason()).isEqualTo(ListingRejectionReason.PHOTOS_UNUSABLE);
            assertThat(evento.occurredAt()).isEqualTo(AHORA);
        }

        @Test
        void deberia_exigir_la_accion() {
            assertThatThrownBy(() -> new ModerationEvent(null, null, AHORA)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void deberia_exigir_la_fecha() {
            assertThatThrownBy(() -> new ModerationEvent(ModerationAction.APPROVED, null, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class ElMotivo {

        /** Aprobar no lleva motivo, y enviar tampoco. Criterio 2. */
        @Test
        void deberia_admitir_una_aprobacion_sin_motivo() {
            ModerationEvent evento = new ModerationEvent(ModerationAction.APPROVED, null, AHORA);

            assertThat(evento.reason()).isNull();
        }

        @Test
        void deberia_admitir_un_envio_sin_motivo() {
            ModerationEvent evento = new ModerationEvent(ModerationAction.SUBMITTED, null, AHORA);

            assertThat(evento.reason()).isNull();
        }

        /**
         * La restriccion que parece natural —RN-022: un rechazo siempre indica motivo— no
         * esta aqui a proposito, y esta prueba lo fija para que nadie la agregue creyendo
         * que faltaba.
         *
         * <p>Esto lee filas ya escritas. Exigir el motivo al leer haria que una sola fila
         * vieja o torcida tumbara el rastro entero de esa publicacion, y quien vende se
         * quedaria sin ver tampoco las que si estan bien. Es el caso borde de la historia:
         * una fila sin motivo donde deberia haberlo se pinta igual, sin inventar texto. La
         * regla se hace cumplir al escribir, que es donde sirve de algo.
         */
        @Test
        void deberia_leer_un_rechazo_sin_motivo_en_vez_de_romperse() {
            ModerationEvent evento = new ModerationEvent(ModerationAction.REJECTED, null, AHORA);

            assertThat(evento.action()).isEqualTo(ModerationAction.REJECTED);
            assertThat(evento.reason()).isNull();
        }
    }
}
