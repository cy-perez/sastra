package co.sendik.identity.model;

/**
 * Lo que queda registrado en la bitacora de una verificacion (HU-002, RN-046).
 *
 * <p>Dos clases de entrada y las dos importan. Las que empiezan por {@code VIEW_} son
 * accesos a un dato sensible: alguien miro la cedula, el reverso, la selfie o el
 * numero de cuenta de otra persona. Las tres ultimas son decisiones sobre su
 * solicitud.
 *
 * <p>Mirar se registra igual que decidir, y esa es la razon por la que estos archivos
 * no se sirven con una URL firmada: un enlace que funciona por si solo no puede
 * registrar quien lo uso (ADR-0018).
 *
 * <p>Los valores son los mismos que admite el {@code CHECK} de
 * {@code verification_access_log} en la migracion V8. Agregar uno aqui exige una
 * migracion que lo agregue alli.
 */
public enum VerificationAccess {
    VIEW_DOCUMENT_FRONT,
    VIEW_DOCUMENT_BACK,
    VIEW_SELFIE,
    VIEW_BANK_ACCOUNT,
    APPROVE,
    REJECT,
    REVOKE
}
