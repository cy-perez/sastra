package co.sastra.catalog.model;

import co.sastra.shared.file.FileKey;
import co.sastra.shared.file.ImageContentType;
import co.sastra.shared.file.ImageDimensions;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Una imagen de la publicacion, sea toma del vendedor o imagen de referencia.
 *
 * <p>Aqui no hay bytes, solo una {@link FileKey}: los archivos viven en el almacen
 * publico y el dominio no los ve nunca (ADR-0018).
 *
 * <p>Las tomas del vendedor van en posiciones 0 a 7 con angulo multiplo de 45
 * (RN-017); las canonicas son las de 0, 90, 180 y 270 y se extraen de esa misma
 * secuencia, no se toman aparte. Una imagen de referencia no tiene angulo ni es
 * canonica: no se tomo girando nada.
 */
public record ProductImage(
        ProductImageId id,
        ImageKind kind,
        FileKey objectKey,
        int position,
        @Nullable Integer angleDegrees,
        ImageDimensions dimensions,
        long bytes,
        ImageContentType contentType) {

    /** RN-017: ocho tomas a 45 grados. */
    public static final int TOMAS_DE_LA_SECUENCIA = 8;

    private static final int GRADOS_POR_PASO = 360 / TOMAS_DE_LA_SECUENCIA;

    public ProductImage {
        Objects.requireNonNull(id, "El identificador es obligatorio");
        Objects.requireNonNull(kind, "La clase de imagen es obligatoria");
        Objects.requireNonNull(objectKey, "La clave del archivo es obligatoria");
        Objects.requireNonNull(dimensions, "Las dimensiones son obligatorias");
        Objects.requireNonNull(contentType, "El tipo de contenido es obligatorio");

        if (position < 0) {
            throw new IllegalArgumentException("La posicion no puede ser negativa: " + position);
        }
        if (bytes <= 0) {
            throw new IllegalArgumentException("El tamano tiene que ser positivo: " + bytes);
        }

        if (kind == ImageKind.SELLER_SHOT) {
            if (position >= TOMAS_DE_LA_SECUENCIA) {
                throw new IllegalArgumentException("Una toma va de 0 a 7 y llego en " + position);
            }
            if (angleDegrees == null || angleDegrees % GRADOS_POR_PASO != 0) {
                throw new IllegalArgumentException("El angulo de una toma es multiplo de 45: " + angleDegrees);
            }
        } else if (angleDegrees != null) {
            throw new IllegalArgumentException("Una imagen de referencia no tiene angulo");
        }
    }

    /** Una toma del vendedor, con su angulo derivado de la posicion. */
    public static ProductImage toma(
            ProductImageId id,
            FileKey clave,
            int posicion,
            ImageDimensions dimensiones,
            long bytes,
            ImageContentType tipo) {

        if (posicion < 0 || posicion >= TOMAS_DE_LA_SECUENCIA) {
            throw new IllegalArgumentException("Una toma va de 0 a 7 y llego en " + posicion);
        }
        return new ProductImage(
                id, ImageKind.SELLER_SHOT, clave, posicion, posicion * GRADOS_POR_PASO, dimensiones, bytes, tipo);
    }

    public static ProductImage referencia(
            ProductImageId id,
            FileKey clave,
            int posicion,
            ImageDimensions dimensiones,
            long bytes,
            ImageContentType tipo) {
        return new ProductImage(id, ImageKind.REFERENCE, clave, posicion, null, dimensiones, bytes, tipo);
    }

    /**
     * RN-016: frontal, lateral derecha, posterior y lateral izquierda.
     *
     * <p>No es un campo guardado sino el angulo: si fuera un campo, podria contradecir
     * a la posicion, y entonces habria dos verdades sobre la misma foto.
     */
    public boolean esCanonica() {
        return kind == ImageKind.SELLER_SHOT && angleDegrees != null && angleDegrees % 90 == 0;
    }

    public boolean esTomaDelVendedor() {
        return kind == ImageKind.SELLER_SHOT;
    }
}
