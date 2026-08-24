package co.sastra.shared.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void deberia_construirse_desde_un_entero_de_pesos() {
        assertThat(Money.dePesos(185_000).enPesos()).isEqualTo(185_000L);
    }

    @Test
    void deberia_rechazar_un_valor_negativo() {
        assertThatThrownBy(() -> Money.dePesos(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negativo");
    }

    @Test
    void deberia_rechazar_decimales_en_vez_de_redondearlos_RN_029() {
        assertThatThrownBy(() -> new Money(new BigDecimal("100.50")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decimales");
    }

    // Un BigDecimal con escala pero sin parte decimal real es el mismo numero, y viene
    // asi de la base de datos cuando la columna es numeric(_, 2). Rechazarlo obligaria
    // a normalizar en cada mapeador.
    @Test
    void deberia_aceptar_una_escala_que_no_lleva_decimales_de_verdad() {
        assertThat(new Money(new BigDecimal("185000.00")).enPesos()).isEqualTo(185_000L);
    }

    @Test
    void deberia_aceptar_el_cero_y_saber_que_lo_es() {
        assertThat(Money.dePesos(0).esCero()).isTrue();
        assertThat(Money.dePesos(1).esCero()).isFalse();
    }

    @Test
    void deberia_comparar_por_valor_y_no_por_escala() {
        Money conEscala = new Money(new BigDecimal("10000.00"));
        Money sinEscala = Money.dePesos(10_000);

        assertThat(conEscala.esMenorQue(sinEscala)).isFalse();
        assertThat(conEscala.esMayorQue(sinEscala)).isFalse();
        assertThat(conEscala).isEqualTo(sinEscala);
    }

    @Test
    void deberia_ordenar_los_limites_de_RN_020() {
        Money minimo = Money.dePesos(10_000);
        Money maximo = Money.dePesos(20_000_000);
        Money dentro = Money.dePesos(185_000);

        assertThat(dentro.esMenorQue(minimo)).isFalse();
        assertThat(dentro.esMayorQue(maximo)).isFalse();
        assertThat(Money.dePesos(9_999).esMenorQue(minimo)).isTrue();
        assertThat(Money.dePesos(20_000_001).esMayorQue(maximo)).isTrue();
    }

    @Test
    void deberia_nombrar_la_moneda_al_imprimirse() {
        assertThat(Money.dePesos(185_000)).hasToString("185000 COP");
    }

    @Test
    void deberia_rechazar_un_valor_nulo() {
        assertThatThrownBy(() -> new Money(null)).isInstanceOf(NullPointerException.class);
    }
}
