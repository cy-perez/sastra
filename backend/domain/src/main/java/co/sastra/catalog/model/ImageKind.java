package co.sastra.catalog.model;

/**
 * De quien es la foto. RN-066.
 *
 * <p>La distincion no es de presentacion: el conteo de tomas obligatorias y el visor
 * 360 solo miran las del vendedor. Con una sola clase de imagen, una publicacion
 * armada con ocho fotos del fabricante pasaria todas las validaciones.
 */
public enum ImageKind {
    /** La tomo el vendedor. Es la unica que cuenta para RN-016 y RN-017. */
    SELLER_SHOT,

    /**
     * Del fabricante. Solo en tecnologia declarada sellada, nunca sustituye a una
     * toma, y la ficha la rotula siempre como referencia.
     */
    REFERENCE
}
