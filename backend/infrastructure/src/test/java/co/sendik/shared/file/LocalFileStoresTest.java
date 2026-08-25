package co.sendik.shared.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Los dos almacenes sobre el sistema de archivos.
 *
 * <p>Lo que importa aqui: que las claves sean opacas e irrepetibles, que los dos
 * almacenes no se pisen, y que borrar no falle nunca —el puerto lo promete y el caso
 * de uso confia en esa promesa hasta el punto de no envolver la llamada en un
 * {@code try}—.
 */
class LocalFileStoresTest {

    @TempDir
    Path raiz;

    private StorageProperties propiedades() {
        return new StorageProperties(
                "local", raiz, URI.create("https://archivos.sendik.co/"), 8_000_000, 200, 200, null, null, null);
    }

    private LocalPublicFileStore publico() {
        return new LocalPublicFileStore(propiedades());
    }

    private LocalRestrictedFileStore reservado() {
        return new LocalRestrictedFileStore(propiedades());
    }

    private static NormalizedImage imagen(String contenido) {
        return new NormalizedImage(
                contenido.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                ImageContentType.PNG,
                new ImageDimensions(400, 400));
    }

    @Test
    void deberia_guardar_y_devolver_una_clave_en_su_carpeta() {
        FileKey clave = publico().guardar("avatares", imagen("una imagen"));

        assertThat(clave.carpeta()).isEqualTo("avatares");
        assertThat(clave.value()).endsWith(".png");
    }

    @Test
    void deberia_escribir_el_contenido_donde_dice_la_clave() throws IOException {
        FileKey clave = publico().guardar("avatares", imagen("una imagen"));

        Path escrito = raiz.resolve("publico").resolve(clave.value());

        assertThat(Files.readString(escrito)).isEqualTo("una imagen");
    }

    /**
     * La clave no lleva nada del nombre original ni de la persona: es un
     * identificador. Un nombre adivinable en un almacen publico convierte "privado
     * por no estar enlazado" en "publico con un paso mas" (ADR-0018).
     */
    @Test
    void nunca_deberia_repetir_una_clave() {
        LocalPublicFileStore almacen = publico();
        Set<String> vistas = new HashSet<>();

        IntStream.range(0, 200)
                .forEach(i ->
                        vistas.add(almacen.guardar("avatares", imagen("x" + i)).value()));

        assertThat(vistas).hasSize(200);
    }

    /**
     * Los dos almacenes son dos carpetas, no un prefijo dentro de la misma. Es lo que
     * hace que el adaptador de la nube sea una traduccion directa, y lo que evita que
     * alguien sirva la raiz "para ver las imagenes en desarrollo" y publique de paso
     * lo reservado.
     */
    @Test
    void los_dos_almacenes_no_deberian_compartir_carpeta() {
        FileKey enPublico = publico().guardar("avatares", imagen("publica"));
        FileKey enReservado = reservado().guardar("documentos", imagen("reservada"));

        assertThat(raiz.resolve("publico").resolve(enPublico.value())).exists();
        assertThat(raiz.resolve("reservado").resolve(enReservado.value())).exists();

        // Y ninguno existe en la carpeta del otro.
        assertThat(raiz.resolve("publico").resolve(enReservado.value())).doesNotExist();
        assertThat(raiz.resolve("reservado").resolve(enPublico.value())).doesNotExist();
    }

    @Test
    void deberia_leer_lo_guardado_en_el_almacen_reservado() {
        LocalRestrictedFileStore almacen = reservado();
        FileKey clave = almacen.guardar("documentos", imagen("la cedula"));

        assertThat(almacen.leer(clave)).asString().isEqualTo("la cedula");
    }

    @Test
    void deberia_borrar_lo_guardado() {
        LocalPublicFileStore almacen = publico();
        FileKey clave = almacen.guardar("avatares", imagen("una imagen"));

        almacen.borrar(clave);

        assertThat(raiz.resolve("publico").resolve(clave.value())).doesNotExist();
    }

    /**
     * Borrar no falla nunca, que es lo que promete el puerto. El caso de uso confia
     * en esa promesa hasta el punto de no envolver la llamada: si esto empezara a
     * lanzar, un cambio de foto correcto acabaria en error para quien no hizo nada
     * mal.
     */
    @Test
    void borrar_lo_que_no_existe_no_deberia_fallar() {
        assertThatCode(() -> publico().borrar(new FileKey("avatares/no-existe.png")))
                .doesNotThrowAnyException();
    }

    @Test
    void borrar_dos_veces_no_deberia_fallar() {
        LocalPublicFileStore almacen = publico();
        FileKey clave = almacen.guardar("avatares", imagen("una imagen"));

        almacen.borrar(clave);

        assertThatCode(() -> almacen.borrar(clave)).doesNotThrowAnyException();
    }

    /** La direccion se compone sin duplicar la barra cuando la base la trae. */
    @Test
    void deberia_componer_la_direccion_publica_sin_barra_doble() {
        FileKey clave = new FileKey("avatares/algo.png");

        assertThat(publico().direccionDe(clave)).hasToString("https://archivos.sendik.co/avatares/algo.png");
    }

    @Test
    void deberia_componer_la_direccion_publica_cuando_la_base_no_trae_barra() {
        StorageProperties sinBarra = new StorageProperties(
                "local", raiz, URI.create("https://archivos.sendik.co"), 8_000_000, 200, 200, null, null, null);

        assertThat(new LocalPublicFileStore(sinBarra).direccionDe(new FileKey("avatares/algo.png")))
                .hasToString("https://archivos.sendik.co/avatares/algo.png");
    }
}
