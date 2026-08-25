package co.sendik.shared.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
