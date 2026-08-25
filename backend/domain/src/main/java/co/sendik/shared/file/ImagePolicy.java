package co.sendik.shared.file;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

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

    /**
     * Tolerancia de la proporcion, en tanto por uno.
     *
     * <p>Existe porque 3:4 exacto no sobrevive al redondeo a pixeles enteros: un recorte
     * legitimo de 901x1200 da 0,7508 y no 0,75. Un uno por ciento admite el redondeo y
     * sigue rechazando cualquier cosa que no sea vertical 3:4.
     */
    private static final double TOLERANCIA_DE_PROPORCION = 0.01;

    private final long bytesMaximos;
    private final ImageDimensions minimo;
    private final @Nullable Double proporcionExigida;

    public ImagePolicy(long bytesMaximos, ImageDimensions minimo) {
        this(bytesMaximos, minimo, null);
    }

    /**
     * Con proporcion exigida, para las tomas de producto.
     *
     * <p>RN-018 fija 3:4 y dice que se recorta en el cliente. El criterio 14 exige que el
     * servidor lo compruebe, y es lo coherente con ADR-0018: si se subiera por URL
     * firmada no habria donde mirarlo, y se eligio subir por el backend justo para no
     * confiar en lo que declara quien sube. Sin esta comprobacion, una sola foto con otra
     * proporcion rompe la altura de toda la fila del catalogo.
     *
     * @param proporcion ancho dividido por alto; {@code null} si no se exige ninguna
     */
    public ImagePolicy(long bytesMaximos, ImageDimensions minimo, @Nullable Double proporcion) {
        if (bytesMaximos <= 0) {
            throw new IllegalArgumentException("El maximo de bytes tiene que ser positivo");
        }
        if (proporcion != null && proporcion <= 0) {
            throw new IllegalArgumentException("La proporcion tiene que ser positiva");
        }
        this.bytesMaximos = bytesMaximos;
        this.minimo = Objects.requireNonNull(minimo, "El minimo de dimensiones es obligatorio");
        this.proporcionExigida = proporcion;
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
        exigirProporcionAceptada(dimensiones);
    }

    /**
     * RN-018 y criterio 14. No hace nada si esta politica no exige proporcion.
     *
     * @throws WrongImageRatioException si se aparta de la proporcion mas de la tolerancia
     */
    public void exigirProporcionAceptada(ImageDimensions dimensiones) {
        if (proporcionExigida == null) {
            return;
        }
        double real = (double) dimensiones.width() / dimensiones.height();

        if (Math.abs(real - proporcionExigida) > TOLERANCIA_DE_PROPORCION) {
            throw new WrongImageRatioException(dimensiones, proporcionExigida);
        }
    }

    public long bytesMaximos() {
        return bytesMaximos;
    }

    public ImageDimensions minimo() {
        return minimo;
    }
}
