package co.sastra.shared.file;

import java.util.Objects;

/**
 * Lo que una imagen tiene que cumplir para aceptarse. Servicio de dominio.
 *
 * <p>Los limites entran por constructor porque son configuracion
 * (`docs/operacion/configuracion.md`): una foto de perfil y una toma de producto no
 * piden lo mismo, y el maximo que aguanta el borde puede cambiar sin que cambie
 * ninguna regla.
 *
 * <p>Tres comprobaciones en tres metodos y no una sola, porque ocurren en momentos
 * distintos de la secuencia y cada una tiene que poder fallar por su cuenta: el
 * tipo y el tamano se miran sobre los bytes que llegan, y las dimensiones solo
 * existen despues de decodificar, que es cosa de un adaptador. Juntarlas en un
 * metodo obligaria a decodificar antes de saber si lo que llego es una imagen.
 */
public final class ImagePolicy {

    private final long bytesMaximos;
    private final ImageDimensions minimo;

    public ImagePolicy(long bytesMaximos, ImageDimensions minimo) {
        if (bytesMaximos <= 0) {
            throw new IllegalArgumentException("El maximo de bytes tiene que ser positivo");
        }
        this.bytesMaximos = bytesMaximos;
        this.minimo = Objects.requireNonNull(minimo, "El minimo de dimensiones es obligatorio");
    }

    /**
     * El tamano se mira antes que nada.
     *
     * <p>Primero porque es lo unico que se puede comprobar sin tocar el contenido:
     * decodificar un archivo enorme para descubrir despues que era enorme es
     * regalarle a quien lo suba el trabajo de decodificarlo.
     *
     * @throws ImageTooLargeException si pasa del maximo
     */
    public void exigirTamanoAceptado(long bytes) {
        if (bytes > bytesMaximos) {
            throw new ImageTooLargeException(bytes, bytesMaximos);
        }
    }

    /**
     * Que tipo es, por su contenido.
     *
     * @throws UnsupportedImageTypeException si no es ninguno de los aceptados, lo
     *     que incluye que no sea una imagen
     */
    public ImageContentType exigirTipoAceptado(byte[] contenido) {
        return ImageContentType.detectar(contenido).orElseThrow(UnsupportedImageTypeException::new);
    }

    /**
     * RN-019.
     *
     * @throws ImageTooSmallException si no alcanza el minimo
     */
    public void exigirDimensionesAceptadas(ImageDimensions dimensiones) {
        if (!dimensiones.alcanza(minimo)) {
            throw new ImageTooSmallException(dimensiones, minimo);
        }
    }

    public long bytesMaximos() {
        return bytesMaximos;
    }

    public ImageDimensions minimo() {
        return minimo;
    }
}
