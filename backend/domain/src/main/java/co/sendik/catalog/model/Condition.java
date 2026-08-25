package co.sendik.catalog.model;

/**
 * Las cuatro condiciones del glosario. No hay una quinta y no se inventa otra escala.
 *
 * <p>Cuales admite una publicacion no lo decide este enum: lo decide su categoria
 * (RN-064). En moda las cuatro; en tecnologia solo {@link #NEW}, porque lo usado se
 * vende unicamente en moda.
 */
public enum Condition {
    NEW,
    LIKE_NEW,
    GOOD,
    WITH_FLAWS;

    /** Lo demas es "de segunda", que es el par comercial de nuevo (glosario). */
    public boolean esNueva() {
        return this == NEW;
    }
}
