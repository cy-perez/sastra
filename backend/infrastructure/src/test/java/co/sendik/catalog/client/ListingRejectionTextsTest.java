package co.sendik.catalog.client;

import static org.assertj.core.api.Assertions.assertThat;

import co.sendik.catalog.model.ListingRejectionReason;
import co.sendik.identity.model.UserLocale;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Los siete motivos de rechazo, en los dos idiomas.
 *
 * <p>El {@code switch} exhaustivo protege contra que falte un texto: agregar un motivo sin
 * el no compila. Lo que no protege es contra un copiar y pegar, que es lo que estas
 * pruebas miran: que los siete digan cosas distintas y que el ingles no sea el espanol.
 */
class ListingRejectionTextsTest {

    @ParameterizedTest
    @EnumSource(ListingRejectionReason.class)
    void deberia_tener_texto_en_los_dos_idiomas(ListingRejectionReason motivo) {
        assertThat(ListingRejectionTexts.de(UserLocale.ES, motivo)).isNotBlank();
        assertThat(ListingRejectionTexts.de(UserLocale.EN, motivo)).isNotBlank();
    }

    /** Ninguno sale como el nombre de la constante: un buzon no tiene quien lo traduzca. */
    @ParameterizedTest
    @EnumSource(ListingRejectionReason.class)
    void ningun_texto_deberia_ser_el_nombre_de_la_enumeracion(ListingRejectionReason motivo) {
        assertThat(ListingRejectionTexts.de(UserLocale.ES, motivo)).doesNotContain(motivo.name());
        assertThat(ListingRejectionTexts.de(UserLocale.EN, motivo)).doesNotContain(motivo.name());
    }

    /**
     * Siete motivos, siete textos distintos.
     *
     * <p>Sin esto, un {@code switch} que devolviera lo mismo para dos motivos pasaria todas
     * las demas pruebas, y el vendedor recibiria el motivo equivocado sin que nada fallara.
     */
    @Test
    void los_siete_motivos_deberian_decir_cosas_distintas() {
        assertThat(textosDe(UserLocale.ES)).doesNotHaveDuplicates();
        assertThat(textosDe(UserLocale.EN)).doesNotHaveDuplicates();
    }

    /** Y el ingles no puede ser el espanol repetido. */
    @ParameterizedTest
    @EnumSource(ListingRejectionReason.class)
    void el_ingles_no_deberia_ser_el_espanol(ListingRejectionReason motivo) {
        assertThat(ListingRejectionTexts.de(UserLocale.EN, motivo))
                .isNotEqualTo(ListingRejectionTexts.de(UserLocale.ES, motivo));
    }

    private static List<String> textosDe(UserLocale idioma) {
        return Arrays.stream(ListingRejectionReason.values())
                .map(motivo -> ListingRejectionTexts.de(idioma, motivo))
                .toList();
    }
}
