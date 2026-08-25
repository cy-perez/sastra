package co.sendik.shared.file;

import co.sendik.shared.port.out.RestrictedFileStore;
import com.google.cloud.storage.Storage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * El almacen reservado sobre Cloud Storage: cedula y selfie (RN-046, ADR-0018).
 *
 * <p>Cubo distinto del publico, no un prefijo dentro de el. La diferencia no es de
 * organizacion: el cubo publico tiene concedida la lectura a {@code allUsers} y este
 * no la tiene nunca, y ese permiso se concede por cubo. Con un solo cubo y dos
 * prefijos, la cedula de alguien seria publica con solo saber su clave.
 *
 * <p>No hay metodo para construir una direccion, igual que en el puerto: lo que se
 * lee de aqui se lee desde el servidor y no sale nunca en una respuesta de la API
 * (docs/operacion/datos-personales.md).
 *
 * <p>Todavia no lo usa nadie: entra con HU-002. Existe ya porque el adaptador de las
 * dos clases de archivo es el mismo trabajo, y porque tenerlo escrito es lo que
 * impide que la selfie acabe en el cubo que cualquiera lee.
 */
@Component
@ConditionalOnProperty(prefix = "sendik.storage", name = "provider", havingValue = "gcs")
public class GcsRestrictedFileStore implements RestrictedFileStore {

    private static final Logger LOG = LoggerFactory.getLogger(GcsRestrictedFileStore.class);

    private final Storage almacen;

    private final String cubo;

    public GcsRestrictedFileStore(Storage almacen, StorageProperties propiedades) {
        this.almacen = almacen;
        this.cubo = propiedades.exigirCuboReservado();
    }

    @Override
    public FileKey guardar(String carpeta, NormalizedImage imagen) {
        return ArchivosEnLaNube.escribir(almacen, cubo, carpeta, imagen);
    }

    @Override
    public byte[] leer(FileKey clave) {
        return ArchivosEnLaNube.leer(almacen, cubo, clave);
    }

    @Override
    public void borrar(FileKey clave) {
        ArchivosEnLaNube.borrarSinFallar(almacen, cubo, clave, LOG);
    }
}
