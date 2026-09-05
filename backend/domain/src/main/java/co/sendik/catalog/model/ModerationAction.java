package co.sendik.catalog.model;

/**
 * Lo que le paso a una publicacion, para el rastro que RN-045 exige.
 *
 * <p><strong>Ya no es «lo que hizo un moderador», y el cambio es de producto.</strong> Lo
 * fue hasta HU-013, cuando hubo que decidir si el rastro cuenta lo que hizo Sendik o lo
 * que le paso a la publicacion. Se eligio lo segundo: sin el envio, el rastro de una
 * publicacion rechazada y reenviada empieza a media frase y no deja ver las dos vueltas,
 * que es el criterio 4. La bitacora sigue llamandose de moderacion porque es el ciclo que
 * anota; lo que cambio es que tambien anota como entro cada vuelta.
 */
public enum ModerationAction {
    /**
     * La publicacion entro a revision. La anota el vendedor, no un moderador.
     *
     * <p>Por los dos caminos que llevan a {@code PENDING_REVIEW} y no solo por el evidente:
     * enviar un borrador, y editar el contenido de una publicacion viva o pausada, que
     * RN-062 devuelve a la cola. Anotar solo el primero dejaria sin rastro justo la vuelta
     * que el vendedor no recuerda haber dado.
     */
    SUBMITTED,
    APPROVED,
    REJECTED,
    /** Bajar una publicacion ya visible que infringe RN-024. */
    ARCHIVED
}
