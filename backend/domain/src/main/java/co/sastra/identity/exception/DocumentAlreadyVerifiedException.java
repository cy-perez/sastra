package co.sastra.identity.exception;

import co.sastra.shared.error.DomainException;
import co.sastra.shared.error.ErrorCode;

/**
 * RN-010 y criterio 5 de HU-002: ese documento ya quedo verificado en otra cuenta.
 *
 * <p>La comprobacion no cabe en el dominio: exige mirar todas las demas
 * verificaciones, y eso lo hace el caso de uso contra el repositorio, por el HMAC
 * indexado de ADR-0020 —el numero cifrado no se puede comparar—. La excepcion vive
 * aqui porque el motivo es de negocio y su codigo pertenece al mismo catalogo.
 *
 * <p>No dice de quien es la otra cuenta ni cual es. Decirlo confirmaria a cualquiera
 * que un documento concreto tiene cuenta en Sastra, con solo escribir numeros.
 */
public final class DocumentAlreadyVerifiedException extends DomainException {

    private static final long serialVersionUID = 1L;

    public DocumentAlreadyVerifiedException() {
        super(ErrorCode.SELLER_DOCUMENT_ALREADY_VERIFIED, "El documento ya esta verificado en otra cuenta (RN-010)");
    }
}
