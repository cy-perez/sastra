package co.sastra.shared.id;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Lo que un identificador ordenable por tiempo tiene que cumplir (ADR-0015). */
class Uuid7Test {

    private static final int LOTE = 10_000;

    @Test
    void deberia_declararse_como_version_7() {
        assertThat(Uuid7.nuevo().version()).isEqualTo(7);
    }

    @Test
    void deberia_declarar_la_variante_de_la_rfc() {
        // 2 es la variante 10 de la RFC 9562. Sin ella, el identificador es un
        // numero de 128 bits que se imprime como UUID y ninguna herramienta lo lee
        // como tal.
        assertThat(Uuid7.nuevo().variant()).isEqualTo(2);
    }

    @Test
    void deberia_llevar_dentro_el_instante_con_el_que_se_creo() {
        Instant instante = Instant.parse("2026-08-21T15:04:05.678Z");

        UUID id = Uuid7.nuevo(instante);

        assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(instante.toEpochMilli());
    }

    @Test
    void deberia_ordenar_por_tiempo_cuando_los_instantes_son_distintos() {
        Instant antes = Instant.parse("2026-08-21T15:04:05.678Z");

        List<String> generados = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            generados.add(Uuid7.nuevo(antes.plusMillis(i)).toString());
        }

        // Comparados como texto a proposito: es el orden que ve el indice de
        // PostgreSQL, y es la propiedad por la que se eligio v7 sobre v4.
        assertThat(generados).isSorted();
    }

    @Test
    void deberia_generar_uno_distinto_cada_vez() {
        Set<UUID> generados = new HashSet<>();
        for (int i = 0; i < LOTE; i++) {
            generados.add(Uuid7.nuevo());
        }

        assertThat(generados).hasSize(LOTE);
    }

    @Test
    void deberia_generar_uno_distinto_cada_vez_dentro_del_mismo_milisegundo() {
        // Sin contador de monotonicidad, dos del mismo milisegundo no ordenan entre
        // si, pero no pueden repetirse: son 74 bits de azar.
        Instant congelado = Instant.parse("2026-08-21T15:04:05.678Z");

        Set<UUID> generados = new HashSet<>();
        for (int i = 0; i < LOTE; i++) {
            generados.add(Uuid7.nuevo(congelado));
        }

        assertThat(generados).hasSize(LOTE);
    }

    @Test
    void deberia_rechazar_un_instante_anterior_a_la_epoca() {
        Instant antiguo = Instant.parse("1969-12-31T23:59:59Z");

        assertThatThrownBy(() -> Uuid7.nuevo(antiguo)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deberia_rechazar_un_instante_que_no_cabe_en_48_bits() {
        Instant lejano = Instant.parse("+11000-01-01T00:00:00Z");

        assertThatThrownBy(() -> Uuid7.nuevo(lejano)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deberia_exigir_un_instante() {
        assertThatThrownBy(() -> Uuid7.nuevo(null)).isInstanceOf(NullPointerException.class);
    }
}
