package co.sastra.shared.file;

import co.sastra.shared.port.out.PublicFileStore;
import com.google.cloud.storage.Storage;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * El almacen publico sobre Cloud Storage (ADR-0018).
 *
 * <p>Su cubo es el que lleva la lectura publica: {@code allUsers} con
 * {@code objectViewer}, concedido una sola vez al crearlo y nunca sobre el cubo
 * reservado (docs/operacion/despliegue.md). Que sean dos cubos y no dos carpetas del
 * mismo es lo que permite dar ese permiso a uno sin darlo al otro.
 *
 * <p>La aplicacion no necesita permiso para hacer publico nada: su cuenta de
 * servicio tiene {@code objectAdmin} sobre los objetos y no {@code admin} sobre el
 * cubo, asi que no puede cambiar la politica de acceso ni por error.
 */
@Component
@ConditionalOnProperty(prefix = "sastra.storage", name = "provider", havingValue = "gcs")
public class GcsPublicFileStore implements PublicFileStore {

    private static final Logger LOG = LoggerFactory.getLogger(GcsPublicFileStore.class);

    private final Storage almacen;

    private final String cubo;

    private final URI base;

    public GcsPublicFileStore(Storage almacen, StorageProperties propiedades) {
        this.almacen = almacen;
        this.cubo = propiedades.exigirCuboPublico();
        this.base = propiedades.publicBaseUrl();
    }

    @Override
    public FileKey guardar(String carpeta, NormalizedImage imagen) {
        return ArchivosEnLaNube.escribir(almacen, cubo, carpeta, imagen);
    }

    @Override
    public void borrar(FileKey clave) {
        ArchivosEnLaNube.borrarSinFallar(almacen, cubo, clave, LOG);
    }

    /**
     * La direccion se compone sobre {@code publicBaseUrl} y no sobre el nombre del
     * cubo.
     *
     * <p>Es lo que permite poner un CDN con dominio propio delante sin tocar el
     * codigo ni la base: en produccion esa variable es el dominio del CDN y aqui no
     * cambia nada. Escribir {@code storage.googleapis.com/<cubo>} a mano ataria cada
     * imagen del HTML servido al proveedor de hoy (ADR-0018).
     */
    @Override
    public URI direccionDe(FileKey clave) {
        String raizPublica = base.toString();
        if (raizPublica.endsWith("/")) {
            raizPublica = raizPublica.substring(0, raizPublica.length() - 1);
        }
        return URI.create(raizPublica + "/" + clave.value());
    }
}
