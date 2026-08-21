package co.sastra.shared.file;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    static FileKey escribir(Path raiz, String carpeta, NormalizedImage imagen) {
        FileKey clave = ClavesDeArchivo.nueva(carpeta, imagen.type());
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
