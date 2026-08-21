package co.sastra.shared.file;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * El nombre con el que un archivo queda guardado. Opaco a proposito.
 *
 * <p>No se deriva del nombre original ni de nada de la persona (ADR-0018). Dos
 * motivos, y el segundo es el que importa: el nombre que trae un archivo subido es
 * entrada del usuario como cualquier otra, y un nombre adivinable en un almacen
 * publico convierte "privado por no estar enlazado" en "publico con un paso mas".
 *
 * <p>El patron es cerrado en lugar de rechazar caracteres peligrosos uno a uno.
 * Prohibir {@code ..} y la barra deja fuera el recorrido de rutas evidente y
 * adentro todo lo que no se penso: el byte nulo, la codificacion en porcentaje que
 * el almacen deshace despues, las barras invertidas de Windows. Con una lista
 * blanca, lo que no se penso no pasa.
 */
public record FileKey(String value) {

    /**
     * Carpeta, barra y nombre. La carpeta agrupa por clase de archivo —{@code
     * avatares/}, {@code documentos/}— y el nombre sale de un identificador
     * aleatorio mas la extension del tipo detectado. Aleatorio y no ordenable por
     * tiempo: es la excepcion que ADR-0015 aparta, porque esta clave se publica.
     */
    private static final Pattern VALIDA = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*/[a-zA-Z0-9_-]+\\.[a-z]{3,4}");

    private static final int LARGO_MAXIMO = 200;

    public FileKey {
        Objects.requireNonNull(value, "La clave del archivo es obligatoria");

        if (value.length() > LARGO_MAXIMO) {
            throw new IllegalArgumentException("La clave del archivo supera los " + LARGO_MAXIMO + " caracteres");
        }
        if (!VALIDA.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "La clave del archivo no tiene la forma carpeta/nombre.ext con caracteres seguros: " + value);
        }
    }

    /** La carpeta, que es lo que agrupa por clase de archivo. */
    public String carpeta() {
        return value.substring(0, value.indexOf('/'));
    }

    @Override
    public String toString() {
        return value;
    }
}
