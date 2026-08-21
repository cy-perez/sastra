package co.sastra.shared.port.out;

import co.sastra.shared.file.FileKey;
import co.sastra.shared.file.NormalizedImage;

/**
 * El almacen de lo que solo ve el proceso de verificacion: cedula y selfie
 * (RN-046).
 *
 * <p>No tiene metodo para construir una direccion publica, y esa ausencia es la
 * mitad de su valor: no existe forma de servir esto por una URL. Lo que se lee de
 * aqui se lee desde el servidor, se registra quien lo hizo, y no sale nunca en una
 * respuesta de la API (RN-046, `docs/operacion/datos-personales.md`).
 *
 * <p>Todavia no lo usa nadie: entra con HU-002. Se declara ahora porque es lo que
 * obliga a que el almacen publico no acabe recibiendo lo que no debe, y porque el
 * adaptador de las dos clases de archivo es el mismo trabajo.
 */
public interface RestrictedFileStore {

    /**
     * Guarda y devuelve la clave.
     *
     * @param carpeta agrupa por clase, por ejemplo {@code documentos} o {@code selfies}
     */
    FileKey guardar(String carpeta, NormalizedImage imagen);

    /** Lee el contenido. Solo desde el servidor y solo el proceso de verificacion. */
    byte[] leer(FileKey clave);

    /** Borra. Idempotente, como el almacen publico. */
    void borrar(FileKey clave);
}
