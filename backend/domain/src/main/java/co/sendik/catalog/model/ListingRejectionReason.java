package co.sendik.catalog.model;

/**
 * Motivos con los que un moderador rechaza una publicacion. RN-022.
 *
 * <p>Lista cerrada y no texto libre, como en la verificacion de vendedor: el texto
 * libre no se traduce, no se puede medir que se rechaza mas, y no le dice al
 * vendedor que corregir, que es justo lo que RN-022 exige. La nota opcional
 * acompana, no sustituye.
 *
 * <p><strong>No es el {@code RejectionReason} de la verificacion.</strong> Son dos
 * listas para dos decisiones distintas y no se mezclan (glosario).
 */
public enum ListingRejectionReason {
    /** RN-016, RN-018, RN-019. */
    PHOTOS_UNUSABLE,
    /** RN-021, RN-050: las fotos no corresponden a lo descrito. */
    PHOTOS_MISMATCH,
    /** RN-021: faltan o no son creibles. */
    MEASUREMENTS_UNRELIABLE,
    /** RN-021, RN-050: la condicion declarada no es la que se ve. */
    CONDITION_MISDECLARED,
    /** RN-024. */
    PROHIBITED_ITEM,
    /** RN-024: se sospecha replica. */
    SUSPECTED_COUNTERFEIT,
    /** RN-020: fuera del rango razonable. */
    PRICE_OUT_OF_RANGE
}
