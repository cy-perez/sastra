package co.sendik.identity.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class EmailTest {

    @Test
    void deberia_normalizar_a_minusculas_para_que_un_correo_sea_una_sola_cuenta_RN_001() {
        assertThat(new Email("Ana@Correo.co")).isEqualTo(new Email("ana@correo.co"));
    }

    @Test
    void deberia_recortar_los_espacios_que_deja_el_autocompletado() {
        assertThat(new Email("  ana@correo.co  ").value()).isEqualTo("ana@correo.co");
    }

    @Test
    void deberia_conservar_el_punto_del_alias_porque_no_todos_los_dominios_lo_ignoran() {
        assertThat(new Email("a.n.a@correo.co").value()).isEqualTo("a.n.a@correo.co");
    }

    @Test
    void deberia_aceptar_subdominios_y_signo_mas() {
        assertThat(new Email("ana+ventas@correo.com.co").value()).isEqualTo("ana+ventas@correo.com.co");
    }

    @Test
    void deberia_rechazar_un_correo_sin_arroba() {
        assertThatThrownBy(() -> new Email("anacorreo.co")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deberia_rechazar_un_correo_sin_dominio_de_primer_nivel() {
        assertThatThrownBy(() -> new Email("ana@correo")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deberia_rechazar_un_correo_con_espacios_interiores() {
        assertThatThrownBy(() -> new Email("an a@correo.co")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deberia_rechazar_un_correo_mas_largo_que_el_limite_de_la_norma() {
        String demasiadoLargo = "a".repeat(250) + "@correo.co";
        assertThatThrownBy(() -> new Email(demasiadoLargo)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deberia_rechazar_un_correo_nulo() {
        assertThatThrownBy(() -> new Email(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void deberia_imprimirse_como_el_correo_normalizado() {
        assertThat(new Email("Ana@Correo.co")).hasToString("ana@correo.co");
    }
}
