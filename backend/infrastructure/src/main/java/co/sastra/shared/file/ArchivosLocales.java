package co.sastra.shared.file;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.slf4j.Logger;

/**
 * Lo comun a los dos almacenes locales: componer la clave, escribir y borrar.
 *
 * <p>Los dos almacenes comparten mecanica y no garantias, asi que comparten estos
 * metodos y siguen siendo dos clases con dos raices distintas. Meterlos en una sola
 * con un parametro devolveria el error que ADR-0018 quiere hacer inexpresable.
 */
final class ArchivosLocales {

    private ArchivosLocales() {}

    /**
     * Un nombre opaco: identificador mas la extension del tipo detectado.
     *
     * <p>Nunca el nombre original. Es entrada del usuario, y un nombre adivinable en
     * un almacen publico convierte "privado por no estar enlazado" en "publico con
     * un paso mas" (ADR-0018).
     *
     * <p><strong>Aqui es v4 y no v7, al contrario que las claves primarias.</strong>
     * Es la excepcion anotada en ADR-0015: de los identificadores del proyecto, este
     * es el unico que sale hacia afuera —viaja en la direccion publica de la
     * imagen—, y un v7 lleva dentro el instante de creacion. Ordenar por tiempo no
     * sirve de nada en el nombre de un archivo, y en cambio publicaria a que hora
     * subio su foto cada persona a quien vea el enlace.
     */
    static FileKey claveNueva(String carpeta, ImageContentType tipo) {
        return new FileKey(carpeta + "/" + UUID.randomUUID() + "." + tipo.extension());
    }

    static FileKey escribir(Path raiz, String carpeta, NormalizedImage imagen) {
        FileKey clave = claveNueva(carpeta, imagen.type());
        Path destino = resolver(raiz, clave);

        try {
            Files.createDirectories(destino.getParent());
            Files.write(destino, imagen.content());
            return clave;
        } catch (IOException fallo) {
            throw new UncheckedIOException("No se pudo guardar el archivo " + clave, fallo);
        }
    }

    static byte[] leer(Path raiz, FileKey clave) {
        try {
            return Files.readAllBytes(resolver(raiz, clave));
        } catch (IOException fallo) {
            throw new UncheckedIOException("No se pudo leer el archivo " + clave, fallo);
        }
    }

    /**
     * Borra sin fallar nunca, que es lo que promete el puerto. Registra lo que queda
     * suelto: es lo unico que permite limpiarlo despues.
     */
    static void borrarSinFallar(Path raiz, FileKey clave, Logger registro) {
        try {
            Files.deleteIfExists(resolver(raiz, clave));
        } catch (IOException | RuntimeException fallo) {
            registro.error(
                    "Quedo un archivo sin borrar en el almacen local: {} ({})",
                    clave,
                    fallo.getClass().getName());
        }
    }

    /**
     * Resuelve la ruta y comprueba que no se salga de la raiz.
     *
     * <p>{@link FileKey} ya rechaza las barras y los puntos dobles, asi que esto es
     * la segunda cerradura de la misma puerta. Va puesta a proposito: aqui se
     * concatenan rutas del sistema de archivos, y si algun dia la clave llegara de
     * otro sitio —una fila escrita a mano, una migracion— esta comprobacion es la
     * que impide escribir fuera del almacen.
     */
    private static Path resolver(Path raiz, FileKey clave) {
        Path normalizada = raiz.resolve(clave.value()).normalize();
        if (!normalizada.startsWith(raiz.normalize())) {
            throw new IllegalArgumentException("La clave apunta fuera del almacen: " + clave);
        }
        return normalizada;
    }
}
