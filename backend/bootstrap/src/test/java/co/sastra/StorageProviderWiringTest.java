package co.sastra;

import static org.assertj.core.api.Assertions.assertThat;

import co.sastra.shared.file.GcsPublicFileStore;
import co.sastra.shared.file.GcsRestrictedFileStore;
import co.sastra.shared.port.out.PublicFileStore;
import co.sastra.shared.port.out.RestrictedFileStore;
import com.google.cloud.storage.Storage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Con {@code provider=gcs} el cableado tiene que elegir los adaptadores de Cloud
 * Storage, y no los locales.
 *
 * <p>Es lo unico de este adaptador que no se puede comprobar con una prueba de
 * unidad: la seleccion la hace {@code @ConditionalOnProperty}, que solo existe dentro
 * de un contexto de Spring. Sin esta prueba, una condicion mal escrita da los dos
 * resultados posibles y los dos son silenciosos: o se crean los dos pares de beans y
 * la inyeccion falla al arrancar, o no se crea ninguno y la aplicacion levanta sin
 * almacen hasta que alguien sube una foto.
 *
 * <p>El cliente de Cloud Storage se sustituye por un doble. No es para evitar la red
 * —guardar aqui no se llama— sino porque construir el de verdad resuelve credenciales
 * al crear el bean, y la verificacion no puede depender de que la maquina donde corre
 * tenga una cuenta de Google. {@code GcsWiring} lo permite con
 * {@code @ConditionalOnMissingBean}, que existe justamente para esto.
 */
@SpringBootTest(
        properties = {
            "sastra.storage.provider=gcs",
            "sastra.storage.public-bucket=cubo-publico-de-prueba",
            "sastra.storage.restricted-bucket=cubo-reservado-de-prueba"
        })
@ActiveProfiles("local")
@Import(PostgresTestContainer.class)
class StorageProviderWiringTest {

    @MockitoBean
    private Storage storage;

    private final PublicFileStore publico;
    private final RestrictedFileStore reservado;

    StorageProviderWiringTest(PublicFileStore publico, RestrictedFileStore reservado) {
        this.publico = publico;
        this.reservado = reservado;
    }

    @Test
    void deberia_cablear_el_almacen_publico_de_cloud_storage() {
        assertThat(publico).isInstanceOf(GcsPublicFileStore.class);
    }

    @Test
    void deberia_cablear_el_almacen_reservado_de_cloud_storage() {
        assertThat(reservado).isInstanceOf(GcsRestrictedFileStore.class);
    }

    /**
     * Y la direccion se compone sobre la base configurada, que es lo que permite poner
     * un CDN con dominio propio delante sin tocar el codigo.
     */
    @Test
    void deberia_componer_la_direccion_publica_sobre_la_base_configurada() {
        assertThat(publico.direccionDe(new co.sastra.shared.file.FileKey("avatares/abc.png")))
                .hasToString("http://localhost:8080/archivos/avatares/abc.png");
    }
}
