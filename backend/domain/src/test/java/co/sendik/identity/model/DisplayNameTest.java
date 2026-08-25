package co.sendik.identity.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DisplayNameTest {

    @Test
    void deberia_recortar_y_colapsar_los_espacios() {
        assertThat(new DisplayName("  Ana   Maria  ").value()).isEqualTo("Ana Maria");
    }

    @Test
    void deberia_rechazar_un_nombre_de_una_sola_letra() {
        assertThatThrownBy(() -> new DisplayName("A")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deberia_rechazar_un_nombre_que_solo_son_espacios() {
        assertThatThrownBy(() -> new DisplayName("     ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deberia_rechazar_un_nombre_demasiado_largo() {
        assertThatThrownBy(() -> new DisplayName("a".repeat(81))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deberia_rechazar_un_nombre_nulo() {
        assertThatThrownBy(() -> new DisplayName(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void deberia_imprimirse_como_el_nombre() {
        assertThat(new DisplayName("Ana Maria")).hasToString("Ana Maria");
    }
}
