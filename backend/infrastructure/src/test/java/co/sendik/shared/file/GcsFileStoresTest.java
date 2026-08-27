package co.sendik.shared.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Los dos almacenes de Cloud Storage (ADR-0018).
 *
 * <p>El cliente se simula. La alternativa —salir a Cloud Storage de verdad— haria que
 * la verificacion dependiera de una cuenta y de la red, y lo que hay que comprobar
 * aqui no esta en la nube: que cada almacen escriba en su cubo y no en el del otro,
 * que la clave sea opaca, que el tipo de contenido viaje, y que borrar no falle
 * nunca. Que Cloud Storage guarde bytes cuando se le pide no es nuestro.
 */
class GcsFileStoresTest {

    private static final String CUBO_PUBLICO = "sendik-publico";

    private static final String CUBO_RESERVADO = "sendik-reservado";

    private final Storage almacen = mock(Storage.class);

    private final StorageProperties propiedades = new StorageProperties(
            "gcs",
            Path.of("no-se-usa"),
            URI.create("https://imagenes.sendik.co"),
            8_000_000,
            200,
            200,
            900,
            1200,
            "sendik-col",
            CUBO_PUBLICO,
            CUBO_RESERVADO);

    private final GcsPublicFileStore publico = new GcsPublicFileStore(almacen, propiedades);

    private final GcsRestrictedFileStore reservado = new GcsRestrictedFileStore(almacen, propiedades);

    private static NormalizedImage imagen() {
        return new NormalizedImage(new byte[] {1, 2, 3}, ImageContentType.PNG, new ImageDimensions(300, 400));
    }

    private BlobInfo capturarEscritura() {
        ArgumentCaptor<BlobInfo> capturado = ArgumentCaptor.forClass(BlobInfo.class);
        verify(almacen).create(capturado.capture(), any(byte[].class));
        return capturado.getValue();
    }

    @Test
    void deberia_guardar_en_el_cubo_publico() {
        publico.guardar("avatares", imagen());

        assertThat(capturarEscritura().getBucket()).isEqualTo(CUBO_PUBLICO);
    }

    /**
     * La prueba que justifica que sean dos clases: guardar una selfie no puede acabar
     * en el cubo que {@code allUsers} puede leer (RN-046).
     */
    @Test
    void deberia_guardar_en_el_cubo_reservado_y_nunca_en_el_publico() {
        reservado.guardar("selfies", imagen());

        assertThat(capturarEscritura().getBucket()).isEqualTo(CUBO_RESERVADO).isNotEqualTo(CUBO_PUBLICO);
    }

    @Test
    void deberia_mandar_el_tipo_de_contenido_detectado() {
        publico.guardar("avatares", imagen());

        // Sin esto Cloud Storage lo sirve como octet-stream y el navegador lo
        // descarga en vez de mostrarlo.
        assertThat(capturarEscritura().getContentType()).isEqualTo("image/png");
    }

    @Test
    void deberia_pedir_que_la_imagen_se_cachee() {
        publico.guardar("avatares", imagen());

        assertThat(capturarEscritura().getCacheControl()).contains("immutable");
    }

    @Test
    void deberia_componer_una_clave_opaca_dentro_de_su_carpeta() {
        FileKey clave = publico.guardar("avatares", imagen());

        assertThat(clave.carpeta()).isEqualTo("avatares");

        // El nombre es un identificador y la extension del tipo detectado, y nada
        // mas: ni el nombre que traia el archivo ni nada de la persona (ADR-0018).
        String nombre = clave.value().substring("avatares/".length());
        assertThat(nombre).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.png");
    }

    @Test
    void deberia_dar_una_clave_distinta_a_cada_imagen() {
        FileKey primera = publico.guardar("avatares", imagen());
        FileKey segunda = publico.guardar("avatares", imagen());

        assertThat(primera).isNotEqualTo(segunda);
    }

    /**
     * La direccion sale de `publicBaseUrl` y no del nombre del cubo: es lo que permite
     * poner un CDN con dominio propio delante sin tocar el codigo ni la base.
     */
    @Test
    void deberia_componer_la_direccion_sobre_la_base_configurada() {
        URI direccion = publico.direccionDe(new FileKey("avatares/abc.png"));

        assertThat(direccion).hasToString("https://imagenes.sendik.co/avatares/abc.png");
    }

    @Test
    void deberia_componer_la_direccion_sin_duplicar_la_barra() {
        StorageProperties conBarra = new StorageProperties(
                "gcs",
                Path.of("no-se-usa"),
                URI.create("https://imagenes.sendik.co/"),
                8_000_000,
                200,
                200,
                900,
                1200,
                null,
                CUBO_PUBLICO,
                CUBO_RESERVADO);

        URI direccion = new GcsPublicFileStore(almacen, conBarra).direccionDe(new FileKey("avatares/abc.png"));

        assertThat(direccion).hasToString("https://imagenes.sendik.co/avatares/abc.png");
    }

    @Test
    void deberia_leer_del_cubo_reservado() {
        when(almacen.readAllBytes(BlobId.of(CUBO_RESERVADO, "selfies/abc.png"))).thenReturn(new byte[] {9});

        assertThat(reservado.leer(new FileKey("selfies/abc.png"))).containsExactly(9);
    }

    @Test
    void deberia_borrar_del_cubo_que_le_corresponde() {
        publico.borrar(new FileKey("avatares/abc.png"));

        verify(almacen).delete(BlobId.of(CUBO_PUBLICO, "avatares/abc.png"));
        verify(almacen, never()).delete(BlobId.of(CUBO_RESERVADO, "avatares/abc.png"));
    }

    /**
     * Lo que promete el puerto: borrar no falla nunca. Quien borra aqui suele estar
     * limpiando la foto anterior, y la operacion que le importaba —la foto nueva— ya
     * salio bien.
     */
    @Test
    void deberia_tragarse_el_fallo_al_borrar() {
        doThrow(new StorageException(503, "no disponible")).when(almacen).delete(any(BlobId.class));

        assertThatCode(() -> publico.borrar(new FileKey("avatares/abc.png"))).doesNotThrowAnyException();
    }

    /**
     * Guardar si falla, y falla con el mismo tipo que el adaptador local: un puerto
     * cuyo contrato de error cambia con el proveedor configurado no es un puerto.
     */
    @Test
    void deberia_fallar_al_guardar_con_el_mismo_tipo_que_el_almacen_local() {
        when(almacen.create(any(BlobInfo.class), any(byte[].class))).thenThrow(new StorageException(500, "fallo"));

        assertThatThrownBy(() -> publico.guardar("avatares", imagen())).isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void deberia_fallar_al_leer_con_el_mismo_tipo_que_el_almacen_local() {
        when(almacen.readAllBytes(any(BlobId.class))).thenThrow(new StorageException(404, "no existe"));

        assertThatThrownBy(() -> reservado.leer(new FileKey("selfies/abc.png")))
                .isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void deberia_exigir_el_cubo_publico_al_construirse() {
        StorageProperties sinCubo = new StorageProperties(
                "gcs",
                Path.of("no-se-usa"),
                URI.create("https://imagenes.sendik.co"),
                8_000_000,
                200,
                200,
                900,
                1200,
                null,
                null,
                CUBO_RESERVADO);

        assertThatThrownBy(() -> new GcsPublicFileStore(almacen, sinCubo))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STORAGE_PUBLIC_BUCKET");
    }

    @Test
    void deberia_exigir_el_cubo_reservado_al_construirse() {
        StorageProperties sinCubo = new StorageProperties(
                "gcs",
                Path.of("no-se-usa"),
                URI.create("https://imagenes.sendik.co"),
                8_000_000,
                200,
                200,
                900,
                1200,
                null,
                CUBO_PUBLICO,
                "   ");

        assertThatThrownBy(() -> new GcsRestrictedFileStore(almacen, sinCubo))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STORAGE_RESTRICTED_BUCKET");
    }

    /**
     * El error que no avisa: con un solo cubo para las dos cosas, todo funciona y la
     * cedula de quien se verifique queda donde {@code allUsers} puede leerla.
     */
    @Test
    void deberia_rechazar_que_los_dos_cubos_sean_el_mismo() {
        assertThatThrownBy(() -> new StorageProperties(
                        "gcs",
                        Path.of("no-se-usa"),
                        URI.create("https://imagenes.sendik.co"),
                        8_000_000,
                        200,
                        200,
                        900,
                        1200,
                        null,
                        CUBO_PUBLICO,
                        CUBO_PUBLICO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no pueden ser el mismo");
    }

    /**
     * Con `local` los dos cubos llegan vacios desde el YAML, y dos vacios iguales no
     * son el mismo cubo: no hay ninguno. Sin esta distincion, la comprobacion de
     * arriba impediria arrancar en desarrollo, que es donde no hay cubos.
     */
    @Test
    void deberia_aceptar_los_dos_cubos_vacios_porque_es_el_caso_de_local() {
        assertThatCode(() -> new StorageProperties(
                        "local",
                        Path.of("./archivos-locales"),
                        URI.create("http://localhost:8080/archivos"),
                        8_000_000,
                        200,
                        200,
                        900,
                        1200,
                        "",
                        "",
                        ""))
                .doesNotThrowAnyException();
    }
}
