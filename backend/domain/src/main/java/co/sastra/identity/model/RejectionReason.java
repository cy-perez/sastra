package co.sastra.identity.model;

/**
 * Por que se rechaza una verificacion. Lista cerrada (criterio 7 de HU-002).
 *
 * <p>Cerrada y no texto libre porque el motivo se traduce y se muestra a la
 * persona, y porque un motivo escrito a mano por quien revisa acaba conteniendo lo
 * que no debe. La nota opcional que acompana al motivo si es texto libre, y por eso
 * tiene su propia regla: viaja a la persona rechazada y nunca lleva informacion
 * judicial ni datos de un tercero.
 */
public enum RejectionReason {

    /** Las fotos no permiten leer el documento. */
    ILLEGIBLE_PHOTOS,

    /** El documento esta vencido. Lo comprueba el moderador sobre la imagen. */
    EXPIRED_DOCUMENT,

    /** RN-012: el titular de la cuenta no es el del documento. */
    HOLDER_MISMATCH,

    /** RN-010 y criterio 5: ese documento ya quedo verificado en otra cuenta. */
    DOCUMENT_ALREADY_VERIFIED,

    /**
     * No cumple los requisitos para vender.
     *
     * <p><strong>Generico a proposito.</strong> Cubre lo que no se puede registrar:
     * la validacion de antecedentes esta fuera del alcance de HU-002, el sistema no
     * consulta ninguna fuente judicial y {@code datos-personales.md} no tiene
     * categoria para un dato asi. Lo que se pierde es que la persona no sabe por que,
     * y se acepta a cambio de no guardar informacion judicial sobre nadie.
     */
    REQUIREMENTS_NOT_MET
}
