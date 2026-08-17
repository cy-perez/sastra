package co.sastra.identity.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class BirthDateTest {

    private static final LocalDate HOY = LocalDate.of(2026, 8, 17);

    @Test
    void deberia_aceptar_a_quien_cumple_dieciocho_hoy_mismo_RN_008() {
        BirthDate cumpleHoy = new BirthDate(HOY.minusYears(18));

        assertThat(cumpleHoy.esMayorDeEdad(HOY)).isTrue();
    }

    @Test
    void deberia_rechazar_a_quien_los_cumple_manana_RN_008() {
        BirthDate cumpleManana = new BirthDate(HOY.minusYears(18).plusDays(1));

        assertThat(cumpleManana.esMayorDeEdad(HOY)).isFalse();
    }

    @Test
    void deberia_aceptar_a_quien_ya_los_cumplio_hace_anos_RN_008() {
        assertThat(new BirthDate(LocalDate.of(1990, 3, 4)).esMayorDeEdad(HOY)).isTrue();
    }

    // El 29 de febrero es el caso que rompe los calculos hechos a mano con dias.
    @Test
    void deberia_calcular_bien_la_edad_de_quien_nacio_un_29_de_febrero() {
        BirthDate bisiesto = new BirthDate(LocalDate.of(2008, 2, 29));

        assertThat(bisiesto.esMayorDeEdad(LocalDate.of(2026, 2, 28))).isFalse();
        assertThat(bisiesto.esMayorDeEdad(LocalDate.of(2026, 3, 1))).isTrue();
    }

    @Test
    void deberia_marcar_como_no_plausible_una_fecha_futura() {
        assertThat(new BirthDate(HOY.plusDays(1)).esPlausible(HOY)).isFalse();
    }

    @Test
    void deberia_marcar_como_no_plausible_un_ano_tecleado_mal() {
        assertThat(new BirthDate(LocalDate.of(1850, 1, 1)).esPlausible(HOY)).isFalse();
    }

    @Test
    void deberia_aceptar_como_plausible_una_fecha_normal() {
        assertThat(new BirthDate(LocalDate.of(1990, 3, 4)).esPlausible(HOY)).isTrue();
    }

    @Test
    void deberia_rechazar_una_fecha_nula() {
        assertThatThrownBy(() -> new BirthDate(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void deberia_exigir_la_fecha_de_referencia_en_vez_de_leer_el_reloj() {
        BirthDate fecha = new BirthDate(LocalDate.of(1990, 3, 4));

        assertThatThrownBy(() -> fecha.esMayorDeEdad(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> fecha.esPlausible(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void deberia_imprimirse_como_la_fecha() {
        assertThat(new BirthDate(LocalDate.of(1990, 3, 4))).hasToString("1990-03-04");
    }
}
