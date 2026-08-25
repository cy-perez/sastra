package co.sendik.catalog.model;

/** Lo que un moderador hizo, para el rastro que RN-045 exige. */
public enum ModerationAction {
    APPROVED,
    REJECTED,
    /** Bajar una publicacion ya visible que infringe RN-024. */
    ARCHIVED
}
