package co.sendik.shared.file;

import java.util.UUID;

/**
 * Compone el nombre con el que un archivo queda guardado.
 *
 * <p>Esta aparte de los adaptadores porque la regla es una sola y no del sistema de
 * archivos ni de Cloud Storage: los dos almacenan lo mismo con el mismo nombre, y
 * duplicarla dejaria dos sitios donde cambiarla y uno donde olvidarlo.
 */
final class ClavesDeArchivo {

    private ClavesDeArchivo() {}

    /**
     * Un nombre opaco: identificador mas la extension del tipo detectado.
     *
     * <p>Nunca el nombre original. Es entrada del usuario, y un nombre adivinable en
     * un almacen publico convierte "privado por no estar enlazado" en "publico con
     * un paso mas" (ADR-0018).
     *
     * <p><strong>Es v4 y no v7, al contrario que las claves primarias.</strong> Es la
     * excepcion anotada en ADR-0015: de los identificadores del proyecto, este es el
     * unico que sale hacia afuera —viaja en la direccion publica de la imagen—, y un
     * v7 lleva dentro el instante de creacion. Ordenar por tiempo no sirve de nada en
     * el nombre de un archivo, y en cambio publicaria a que hora subio su foto cada
     * persona a quien vea el enlace.
     */
    static FileKey nueva(String carpeta, ImageContentType tipo) {
        return new FileKey(carpeta + "/" + UUID.randomUUID() + "." + tipo.extension());
    }
}
