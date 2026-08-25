package co.sendik.shared.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * El tipo se decide por el contenido, y esto es lo que lo prueba.
 *
 * <p>La prueba que importa es la ultima: un archivo que se llama {@code .jpg}, se
 * declara {@code image/jpeg} y dentro trae HTML con un script. Servido despues desde
 * el dominio del sitio es un XSS almacenado, y la victima no puede distinguirlo de
 * una imagen porque llega de donde llegan las imagenes de verdad (ADR-0018).
 */
class ImageContentTypeTest {

    private static byte[] bytes(int... valores) {
        byte[] salida = new byte[valores.length];
        for (int posicion = 0; posicion < valores.length; posicion++) {
            salida[posicion] = (byte) valores[posicion];
        }
        return salida;
    }

    @Test
    void deberia_reconocer_jpeg_por_su_firma() {
        assertThat(ImageContentType.detectar(bytes(0xFF, 0xD8, 0xFF, 0xE0))).contains(ImageContentType.JPEG);
    }

    @Test
    void deberia_reconocer_png_por_su_firma() {
        assertThat(ImageContentType.detectar(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)))
                .contains(ImageContentType.PNG);
    }

    /**
     * WebP no se acepta, y no por gusto: {@code javax.imageio} no trae lector, asi
     * que no se puede decodificar ni recodificar para quitarle el EXIF. Aceptarlo
     * seria prometer lo que el normalizador no puede cumplir.
     */
    @Test
    void no_deberia_aceptar_webp_porque_el_jdk_no_lo_lee() {
        byte[] webp = bytes(0x52, 0x49, 0x46, 0x46, 0x00, 0x00, 0x00, 0x00, 0x57, 0x45, 0x42, 0x50);

        assertThat(ImageContentType.detectar(webp)).isEmpty();
    }

    /** GIF y SVG no entran: el primero anima y el segundo es XML que ejecuta. */
    @Test
    void no_deberia_aceptar_gif_ni_svg() {
        assertThat(ImageContentType.detectar("GIF89a".getBytes(StandardCharsets.US_ASCII)))
                .isEmpty();
        assertThat(ImageContentType.detectar("<svg xmlns=".getBytes(StandardCharsets.US_ASCII)))
                .isEmpty();
    }

    @Test
    void no_deberia_aceptar_contenido_vacio_ni_nulo() {
        assertThat(ImageContentType.detectar(new byte[0])).isEmpty();
        assertThat(ImageContentType.detectar(null)).isEmpty();
    }

    /** Un archivo mas corto que la firma no puede coincidir con ella. */
    @Test
    void no_deberia_desbordarse_con_un_archivo_mas_corto_que_la_firma() {
        assertThat(ImageContentType.detectar(bytes(0x89, 0x50))).isEmpty();
    }

    /**
     * La prueba que justifica todo lo demas: HTML con un script, con nombre y tipo
     * declarado de imagen. Aqui no llega el nombre ni el tipo declarado, y por eso
     * la respuesta es la correcta.
     */
    @Test
    void no_deberia_aceptar_html_disfrazado_de_imagen() {
        byte[] disfrazado = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);

        Optional<ImageContentType> tipo = ImageContentType.detectar(disfrazado);

        assertThat(tipo).isEmpty();
    }

    /** La extension solo sirve para componer la clave, nunca para decidir. */
    @Test
    void deberia_dar_una_extension_por_tipo() {
        assertThat(ImageContentType.JPEG.extension()).isEqualTo("jpg");
        assertThat(ImageContentType.PNG.extension()).isEqualTo("png");
    }

    /**
     * Todo tipo aceptado tiene que poder decodificarse y volver a escribirse. Si
     * algun dia se agrega uno que el JDK no sepa escribir, esta prueba lo dice antes
     * de que lo diga una persona a la que se le rechazo la foto.
     */
    @Test
    void todo_tipo_aceptado_deberia_poder_leerse_y_escribirse() {
        for (ImageContentType tipo : ImageContentType.values()) {
            assertThat(javax.imageio.ImageIO.getImageWritersByFormatName(tipo.extension()))
                    .as("escritor para %s", tipo)
                    .hasNext();
        }
    }
}
