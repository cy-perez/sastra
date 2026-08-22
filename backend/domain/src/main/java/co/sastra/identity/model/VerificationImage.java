package co.sastra.identity.model;

/**
 * Cual de las tres imagenes de una verificacion se pide.
 *
 * <p>Un enum y no la clave del archivo en la peticion, y esa es la diferencia que
 * importa: con la clave en la URL, quien la tuviera podria pedir cualquier archivo del
 * almacen reservado. Con un enum, lo unico que se puede pedir es "el frente de esta
 * solicitud", y quien resuelve cual es esa clave es el servidor.
 *
 * <p>Cada valor sabe que se anota en la bitacora al servirlo (RN-046).
 */
public enum VerificationImage {
    DOCUMENT_FRONT(VerificationAccess.VIEW_DOCUMENT_FRONT),
    DOCUMENT_BACK(VerificationAccess.VIEW_DOCUMENT_BACK),
    SELFIE(VerificationAccess.VIEW_SELFIE);

    private final VerificationAccess acceso;

    VerificationImage(VerificationAccess acceso) {
        this.acceso = acceso;
    }

    public VerificationAccess acceso() {
        return acceso;
    }
}
