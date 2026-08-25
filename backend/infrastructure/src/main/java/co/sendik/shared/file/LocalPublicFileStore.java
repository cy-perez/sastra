package co.sendik.shared.file;

import co.sendik.shared.port.out.PublicFileStore;
import java.net.URI;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * El almacen publico sobre el sistema de archivos. Desarrollo y pruebas.
 *
 * <p>Existe para poder recorrer la subida completa sin credenciales de ningun
 * proveedor, igual que el adaptador de consola del correo (ADR-0012). En la nube no
 * se usa nunca y no serviria: el sistema de archivos de Cloud Run es efimero, asi
 * que lo guardado desaparece con la instancia.
 */
@Component
@ConditionalOnProperty(prefix = "sendik.storage", name = "provider", havingValue = "local")
public class LocalPublicFileStore implements PublicFileStore {

    private static final Logger LOG = LoggerFactory.getLogger(LocalPublicFileStore.class);

    /** Carpeta propia, separada de la reservada. Son dos almacenes tambien aqui. */
    private final Path raiz;

    private final URI base;

    public LocalPublicFileStore(StorageProperties propiedades) {
        this.raiz = propiedades.localPath().resolve("publico");
        this.base = propiedades.publicBaseUrl();
    }

    @Override
    public FileKey guardar(String carpeta, NormalizedImage imagen) {
        return ArchivosLocales.escribir(raiz, carpeta, imagen);
    }

    @Override
    public void borrar(FileKey clave) {
        ArchivosLocales.borrarSinFallar(raiz, clave, LOG);
    }

    /**
     * En local la direccion la sirve quien haya montado la carpeta: el servidor de
     * desarrollo del frontend, o nada. Se compone igual para que el contrato de la
     * API sea el mismo en los dos entornos y el frontend no tenga dos caminos.
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
