package co.sendik.shared.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Lo que una imagen tiene que cumplir para aceptarse. */
class ImagePolicyTest {

    private static final long MAXIMO = 1_000;

    private final ImagePolicy politica = new ImagePolicy(MAXIMO, new ImageDimensions(200, 200));

    @Test
    void deberia_aceptar_lo_que_esta_dentro_del_maximo() {
        assertThatCode(() -> politica.exigirTamanoAceptado(MAXIMO)).doesNotThrowAnyException();
    }

    @Test
    void deberia_rechazar_lo_que_pasa_del_maximo() {
        assertThatThrownBy(() -> politica.exigirTamanoAceptado(MAXIMO + 1)).isInstanceOf(ImageTooLargeException.class);
    }

    @Test
    void deberia_aceptar_una_imagen_de_un_tipo_conocido() {
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

        assertThat(politica.exigirTipoAceptado(png)).isEqualTo(ImageContentType.PNG);
    }

    @Test
    void deberia_rechazar_lo_que_no_es_una_imagen_aceptada() {
        assertThatThrownBy(() -> politica.exigirTipoAceptado(new byte[] {1, 2, 3}))
                .isInstanceOf(UnsupportedImageTypeException.class);
    }

    @Test
    void deberia_aceptar_las_dimensiones_justas() {
        assertThatCode(() -> politica.exigirDimensionesAceptadas(new ImageDimensions(200, 200)))
                .doesNotThrowAnyException();
    }

    /** Un solo pixel de menos en un lado ya no alcanza: el minimo es por lado. */
    @Test
    void deberia_rechazar_si_falla_un_solo_lado() {
        assertThatThrownBy(() -> politica.exigirDimensionesAceptadas(new ImageDimensions(199, 400)))
                .isInstanceOf(ImageTooSmallException.class);
        assertThatThrownBy(() -> politica.exigirDimensionesAceptadas(new ImageDimensions(400, 199)))
                .isInstanceOf(ImageTooSmallException.class);
    }

    /**
     * El mensaje interno lleva las medidas, que no son dato personal y son lo unico
     * que hace util el registro cuando alguien reporta que su foto se rechazo.
     */
    @Test
    void deberia_decir_en_el_error_cuanto_media_y_cuanto_hacia_falta() {
        assertThatThrownBy(() -> politica.exigirDimensionesAceptadas(new ImageDimensions(10, 10)))
                .hasMessageContaining("10x10")
                .hasMessageContaining("200x200");
    }

    @Test
    void no_deberia_construirse_con_un_maximo_absurdo() {
        assertThatThrownBy(() -> new ImagePolicy(0, new ImageDimensions(1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * La politica de las tomas de producto: RN-018 y RN-019.
     *
     * <p>El constructor de tres argumentos existia sin usarse ni probarse, y por eso nadie
     * noto que el catalogo recibia la politica del avatar. Estas pruebas fijan lo que la
     * regla dice: 900x1200 de minimo y proporcion 3:4.
     */
    @Nested
    class ComoLaDeLasTomas {

        private final ImagePolicy tomas = new ImagePolicy(MAXIMO, new ImageDimensions(900, 1200), 0.75);

        @Test
        void deberia_aceptar_el_minimo_exacto_de_RN_019() {
            assertThatCode(() -> tomas.exigirDimensionesAceptadas(new ImageDimensions(900, 1200)))
                    .doesNotThrowAnyException();
        }

        @Test
        void deberia_aceptar_una_mas_grande_con_la_misma_proporcion() {
            assertThatCode(() -> tomas.exigirDimensionesAceptadas(new ImageDimensions(1200, 1600)))
                    .doesNotThrowAnyException();
        }

        /** El tamano que acepta el avatar. Si esta pasa, es que se aplico la politica que no era. */
        @Test
        void deberia_rechazar_lo_que_no_llega_al_minimo_RN_019() {
            assertThatThrownBy(() -> tomas.exigirDimensionesAceptadas(new ImageDimensions(200, 200)))
                    .isInstanceOf(ImageTooSmallException.class);
        }

        @Test
        void deberia_rechazar_una_cuadrada_aunque_sea_grande_RN_018() {
            assertThatThrownBy(() -> tomas.exigirDimensionesAceptadas(new ImageDimensions(1600, 1600)))
                    .isInstanceOf(WrongImageRatioException.class);
        }

        /** Apaisada: cumple el minimo por los dos lados y la proporcion esta del reves. */
        @Test
        void deberia_rechazar_una_apaisada_RN_018() {
            assertThatThrownBy(() -> tomas.exigirDimensionesAceptadas(new ImageDimensions(1600, 1200)))
                    .isInstanceOf(WrongImageRatioException.class);
        }

        /** La tolerancia del 1% existe para el redondeo del recorte en cliente, no para otra forma. */
        @Test
        void deberia_aceptar_una_desviacion_dentro_de_la_tolerancia() {
            assertThatCode(() -> tomas.exigirDimensionesAceptadas(new ImageDimensions(902, 1200)))
                    .doesNotThrowAnyException();
        }
    }
}
