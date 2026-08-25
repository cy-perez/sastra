package co.sendik.identity.exception;

import co.sendik.shared.error.DomainException;
import co.sendik.shared.error.ErrorCode;

/**
 * RN-060: quien revisa y quien es revisado tienen que ser dos personas.
 *
 * <p>No es una sospecha sobre nadie. El sello de verificado es lo que responde por una
 * transaccion ante quien compra, y un sello que alguien puede otorgarse a si mismo no
 * responde por nada.
 *
 * <p>La comprobacion vive en el caso de uso y no en el dominio: comparar el actor con el
 * dueno exige los dos, y {@code SellerVerification} solo se conoce a si misma. La
 * excepcion vive aqui porque el motivo es de negocio y su codigo pertenece al mismo
 * catalogo.
 *
 * <p>Cubre aprobar y rechazar, que es lo que RN-060 nombra. Revocar queda fuera a
 * proposito: revocarse el propio sello es autoperjuicio, no autoconcesion, y no es el
 * riesgo que la regla ataca.
 */
public final class SelfReviewForbiddenException extends DomainException {

    private static final long serialVersionUID = 1L;

    public SelfReviewForbiddenException() {
        super(ErrorCode.SELLER_SELF_REVIEW_FORBIDDEN, "Un moderador no decide sobre su propia solicitud (RN-060)");
    }
}
