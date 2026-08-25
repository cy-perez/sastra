package co.sendik.shared.file;

import co.sendik.shared.port.out.RestrictedFileStore;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * El almacen reservado sobre el sistema de archivos. Desarrollo y pruebas.
 *
 * <p>Carpeta distinta de la publica, no un prefijo dentro de ella. En local nadie
 * sirve estas carpetas por HTTP, asi que la separacion no cambia quien puede leer
 * que; se hace igual porque es lo que hace que el adaptador de la nube sea una
 * traduccion directa y no un rediseno, y porque una carpeta compartida invita a que
 * alguien sirva la raiz "para ver las imagenes en desarrollo".
 */
@Component
@ConditionalOnProperty(prefix = "sendik.storage", name = "provider", havingValue = "local")
public class LocalRestrictedFileStore implements RestrictedFileStore {

    private static final Logger LOG = LoggerFactory.getLogger(LocalRestrictedFileStore.class);

    private final Path raiz;

    public LocalRestrictedFileStore(StorageProperties propiedades) {
        this.raiz = propiedades.localPath().resolve("reservado");
    }

    @Override
    public FileKey guardar(String carpeta, NormalizedImage imagen) {
        return ArchivosLocales.escribir(raiz, carpeta, imagen);
    }

    @Override
    public byte[] leer(FileKey clave) {
        return ArchivosLocales.leer(raiz, clave);
    }

    @Override
    public void borrar(FileKey clave) {
        ArchivosLocales.borrarSinFallar(raiz, clave, LOG);
    }
}
