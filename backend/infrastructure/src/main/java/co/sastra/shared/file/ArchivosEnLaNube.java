package co.sastra.shared.file;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.slf4j.Logger;

/**
 * Lo comun a los dos almacenes de Cloud Storage: escribir, leer y borrar.
 *
 * <p>Es la traduccion directa de {@link ArchivosLocales}, y lo es a proposito: los
 * dos almacenes se separan por cubo igual que alli por carpeta, asi que el adaptador
 * de la nube resulto ser una traduccion y no un rediseno. Siguen siendo dos clases
 * con dos cubos distintos, porque un solo almacen con un parametro de visibilidad
 * devolveria el error que ADR-0018 quiere hacer inexpresable.
 *
 * <p><strong>Los fallos se envuelven en {@link UncheckedIOException}</strong>, que es
 * lo que lanza el adaptador local. No es la excepcion natural de esta libreria
 * —{@link StorageException} ya es de ejecucion y se podria dejar pasar—, pero
 * dejarla pasar significaria que quien llama al puerto ve un tipo distinto segun el
 * proveedor configurado. Un puerto cuyo contrato de error cambia con el adaptador no
 * es un puerto.
 */
final class ArchivosEnLaNube {

    private ArchivosEnLaNube() {}

    /**
     * Escribe el objeto y devuelve la clave con la que quedo.
     *
     * <p>Se manda el tipo de contenido detectado y no el declarado por quien subio:
     * sin el, Cloud Storage sirve el objeto como {@code application/octet-stream} y
     * el navegador lo descarga en vez de mostrarlo.
     */
    static FileKey escribir(Storage almacen, String cubo, String carpeta, NormalizedImage imagen) {
        FileKey clave = ClavesDeArchivo.nueva(carpeta, imagen.type());

        BlobInfo objeto = BlobInfo.newBuilder(BlobId.of(cubo, clave.value()))
                .setContentType(imagen.type().mediaType())
                // La clave es un identificador aleatorio que no se reutiliza nunca:
                // el contenido de esta direccion no puede cambiar, solo dejar de
                // existir. Sin esta cabecera, cada visita al catalogo vuelve a
                // descargar imagenes que ya tenia.
                .setCacheControl("public, max-age=31536000, immutable")
                .build();

        try {
            almacen.create(objeto, imagen.content());
            return clave;
        } catch (StorageException fallo) {
            throw new UncheckedIOException(
                    "No se pudo guardar el archivo " + clave + " en el cubo " + cubo, new IOException(fallo));
        }
    }

    static byte[] leer(Storage almacen, String cubo, FileKey clave) {
        try {
            return almacen.readAllBytes(BlobId.of(cubo, clave.value()));
        } catch (StorageException fallo) {
            throw new UncheckedIOException(
                    "No se pudo leer el archivo " + clave + " del cubo " + cubo, new IOException(fallo));
        }
    }

    /**
     * Borra sin fallar nunca, que es lo que promete el puerto. Registra lo que queda
     * suelto: es lo unico que permite limpiarlo despues.
     *
     * <p>Que el objeto no existiera no se registra como incidencia. {@code delete}
     * devuelve {@code false} y ya esta: borrar dos veces es el caso normal de un
     * reintento, no un archivo huerfano.
     */
    static void borrarSinFallar(Storage almacen, String cubo, FileKey clave, Logger registro) {
        try {
            almacen.delete(BlobId.of(cubo, clave.value()));
        } catch (RuntimeException fallo) {
            registro.error(
                    "Quedo un archivo sin borrar en el cubo {}: {} ({})",
                    cubo,
                    clave,
                    fallo.getClass().getName());
        }
    }
}
