package co.sastra.shared.file;

import java.util.Optional;

/**
 * Los tipos de imagen que se aceptan, reconocidos por los bytes de cabecera.
 *
 * <p><strong>Ni la extension ni el {@code Content-Type} sirven para esto.</strong>
 * Los dos los pone quien sube el archivo, asi que los dos son entrada del usuario.
 * Un archivo llamado {@code foto.jpg}, declarado como {@code image/jpeg} y que en
 * realidad contiene HTML con un script, servido despues desde el dominio del
 * sitio, es un XSS almacenado: la victima no tiene forma de distinguirlo de una
 * imagen porque llega de donde llegan las imagenes de verdad.
 *
 * <p>Los bytes de cabecera si son del archivo. No son infalsificables —se puede
 * escribir un archivo que empiece por la firma de JPEG y siga con otra cosa—, pero
 * combinados con volver a codificar la imagen antes de guardarla (ADR-0018) lo que
 * queda almacenado es siempre el resultado de decodificar y recodificar una
 * imagen: si no era una imagen, no hay nada que guardar.
 *
 * <p><strong>Dos formatos, y WebP no esta.</strong> No es por seguridad: es que
 * {@code javax.imageio} no trae lector de WebP —comprobado en el JDK 25, que lee
 * JPEG, PNG, BMP, GIF, TIFF y WBMP— y sin lector no se puede decodificar, asi que
 * tampoco se puede recodificar para quitarle el EXIF. Aceptarlo aqui seria prometer
 * algo que el normalizador no puede cumplir: cada WebP acabaria rechazado con "no se
 * pudo decodificar" en lugar de con "este tipo no se acepta", que es lo que de
 * verdad pasa. Anadirlo exige una libreria con decodificador de WebP, y esa decision
 * tiene sentido cuando HU-003 mida cuanto ahorra en las ocho tomas por prenda.
 *
 * <p>GIF y SVG tampoco entran, y estos si por lo que son: el primero anima y el
 * segundo es XML que ejecuta. Cada formato aceptado es un decodificador mas expuesto
 * a lo que suba cualquiera.
 */
public enum ImageContentType {

    /** {@code FF D8 FF}. El cuarto byte varia segun el tipo de marcador. */
    JPEG("image/jpeg", "jpg", new int[] {0xFF, 0xD8, 0xFF}),

    /** {@code 89 50 4E 47 0D 0A 1A 0A}, la firma completa de PNG. */
    PNG("image/png", "png", new int[] {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});

    private final String mediaType;
    private final String extension;
    private final int[] firma;

    ImageContentType(String mediaType, String extension, int[] firma) {
        this.mediaType = mediaType;
        this.extension = extension;
        this.firma = firma;
    }

    public String mediaType() {
        return mediaType;
    }

    /** Solo para componer la clave del archivo. No se lee de lo que subio nadie. */
    public String extension() {
        return extension;
    }

    /**
     * Que tipo es, mirando el contenido.
     *
     * @return vacio si no es ninguno de los aceptados, lo que incluye el caso de
     *     que no sea una imagen en absoluto
     */
    public static Optional<ImageContentType> detectar(byte[] contenido) {
        if (contenido == null) {
            return Optional.empty();
        }

        for (ImageContentType tipo : values()) {
            if (tipo.coincideLaFirma(contenido)) {
                return Optional.of(tipo);
            }
        }
        return Optional.empty();
    }

    private boolean coincideLaFirma(byte[] contenido) {
        if (contenido.length < firma.length) {
            return false;
        }
        for (int posicion = 0; posicion < firma.length; posicion++) {
            if ((contenido[posicion] & 0xFF) != firma[posicion]) {
                return false;
            }
        }
        return true;
    }
}
