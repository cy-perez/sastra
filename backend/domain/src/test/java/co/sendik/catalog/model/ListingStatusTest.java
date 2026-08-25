package co.sendik.catalog.model;

import static co.sendik.catalog.model.ListingStatus.ARCHIVED;
import static co.sendik.catalog.model.ListingStatus.DRAFT;
import static co.sendik.catalog.model.ListingStatus.PAUSED;
import static co.sendik.catalog.model.ListingStatus.PENDING_REVIEW;
import static co.sendik.catalog.model.ListingStatus.PUBLISHED;
import static co.sendik.catalog.model.ListingStatus.REJECTED;
import static co.sendik.catalog.model.ListingStatus.SOLD;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * La tabla de RN-061, entera y en los dos sentidos.
 *
 * <p>Se comprueban las validas <strong>y</strong> que ninguna otra exista. Probar solo
 * las validas dejaria pasar una tabla demasiado permisiva, que es el error que de
 * verdad importa aqui: una transicion de mas es una publicacion que se salta la
 * moderacion.
 */
class ListingStatusTest {

    /** Copia literal de la tabla de RN-061 en reglas-negocio.md. */
    private static final Map<ListingStatus, Set<ListingStatus>> ESPERADAS = Map.of(
            DRAFT, EnumSet.of(DRAFT, PENDING_REVIEW, ARCHIVED),
            PENDING_REVIEW, EnumSet.of(DRAFT, PUBLISHED, REJECTED),
            PUBLISHED, EnumSet.of(PENDING_REVIEW, PAUSED, SOLD, ARCHIVED),
            REJECTED, EnumSet.of(DRAFT, ARCHIVED),
            PAUSED, EnumSet.of(PUBLISHED, PENDING_REVIEW, ARCHIVED),
            SOLD, EnumSet.noneOf(ListingStatus.class),
            ARCHIVED, EnumSet.noneOf(ListingStatus.class));

    @Test
    void deberia_admitir_exactamente_las_transiciones_de_RN_061_y_ninguna_mas() {
        for (ListingStatus desde : ListingStatus.values()) {
            for (ListingStatus hacia : ListingStatus.values()) {
                boolean esperada = ESPERADAS.get(desde).contains(hacia);

                assertThat(desde.puedePasarA(hacia))
                        .withFailMessage(
                                "De %s a %s deberia ser %s y es %s", desde, hacia, esperada, desde.puedePasarA(hacia))
                        .isEqualTo(esperada);
            }
        }
    }

    @Test
    void deberia_dejar_vendida_y_archivada_como_terminales_RN_023() {
        assertThat(SOLD.esTerminal()).isTrue();
        assertThat(ARCHIVED.esTerminal()).isTrue();

        assertThat(EnumSet.of(DRAFT, PENDING_REVIEW, PUBLISHED, REJECTED, PAUSED))
                .allSatisfy(estado -> assertThat(estado.esTerminal()).isFalse());
    }

    @Test
    void deberia_hacer_visible_solo_lo_publicado() {
        assertThat(PUBLISHED.esVisible()).isTrue();

        assertThat(EnumSet.complementOf(EnumSet.of(PUBLISHED)))
                .allSatisfy(estado -> assertThat(estado.esVisible()).isFalse());
    }

    // Pausar no cambia nada de lo que un moderador aprobo, asi que reanudar no puede
    // exigir revisarlo otra vez.
    @Test
    void deberia_dejar_reanudar_una_pausada_sin_pasar_por_moderacion() {
        assertThat(PAUSED.puedePasarA(PUBLISHED)).isTrue();
    }

    // Al reves que RN-059: alli una cedula ya vista no se retira, aqui solo se retira
    // la foto de un producto.
    @Test
    void deberia_dejar_retirar_una_solicitud_a_diferencia_de_la_verificacion_RN_061() {
        assertThat(PENDING_REVIEW.puedePasarA(DRAFT)).isTrue();
    }

    @Test
    void deberia_admitir_edicion_libre_solo_en_borrador() {
        assertThat(DRAFT.admiteEdicionLibre()).isTrue();

        assertThat(EnumSet.complementOf(EnumSet.of(DRAFT)))
                .allSatisfy(estado -> assertThat(estado.admiteEdicionLibre()).isFalse());
    }

    @Test
    void deberia_entregar_los_destinos_sin_dejar_modificar_la_tabla() {
        Set<ListingStatus> destinos = DRAFT.destinos();
        destinos.clear();

        assertThat(DRAFT.destinos()).containsExactlyInAnyOrder(DRAFT, PENDING_REVIEW, ARCHIVED);
    }
}
