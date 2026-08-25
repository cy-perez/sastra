package co.sendik.catalog.exception;

import co.sendik.shared.error.DomainException;
import co.sendik.shared.error.ErrorCode;

/**
 * RN-063: un moderador no decide sobre su propia publicacion.
 *
 * <p>Es RN-060 aplicada al catalogo. La moderacion es lo que responde ante el
 * comprador de que lo publicado es lo que dice ser, y una publicacion que su propio
 * dueno aprueba no responde por nada.
 *
 * <p>La comprobacion vive en el caso de uso y no en el dominio: comparar el actor con
 * el dueno exige tener los dos delante, y una publicacion solo se conoce a si misma.
 */
public final class SelfModerationForbiddenException extends DomainException {

    private static final long serialVersionUID = 1L;

    public SelfModerationForbiddenException() {
        super(
                ErrorCode.CATALOG_SELF_MODERATION_FORBIDDEN,
                "Un moderador no decide sobre su propia publicacion (RN-063)");
    }
}
