package co.sastra.catalog.model;

/**
 * Por que una publicacion se marca para revision mas atenta.
 *
 * <p>No cambia el estado ni bloquea nada: solo hace que el moderador la vea
 * destacada. Es lo que RN-020 llama "revision manual" para un precio fuera de rango,
 * y lo que el criterio 8 de HU-003 pide para una toma que no se capturo.
 */
public enum AttentionReason {
    /** RN-020: el precio esta fuera de [10.000, 20.000.000]. */
    PRICE_OUT_OF_RANGE,

    /**
     * La toma se cargo desde la galeria en vez de capturarse.
     *
     * <p>Lo declara el cliente, porque el servidor no lo puede distinguir. Por eso
     * solo suma una marca y nunca quita una validacion.
     */
    GALLERY_UPLOAD
}
