package co.sastra.identity.exception;

import co.sastra.identity.model.VerificationStatus;
import co.sastra.shared.error.DomainException;
import co.sastra.shared.error.ErrorCode;

/**
 * RN-059: se pidio una transicion que la verificacion no admite desde su estado.
 *
 * <p>Es un conflicto y no un error de programacion: se llega aqui pulsando dos veces
 * el boton de enviar, o volviendo con el boton de atras a una pantalla que ya no
 * corresponde. Por eso lleva codigo de error y no es una {@code IllegalStateException}.
 *
 * <p>El mensaje interno nombra los dos estados porque es lo unico que hace falta para
 * entender el registro; hacia afuera solo sale el codigo.
 */
public final class InvalidVerificationTransitionException extends DomainException {

    private static final long serialVersionUID = 1L;

    public InvalidVerificationTransitionException(VerificationStatus desde, VerificationStatus hacia) {
        super(
                ErrorCode.SELLER_VERIFICATION_INVALID_STATE,
                "La verificacion no puede pasar de " + desde + " a " + hacia + " (RN-059)");
    }
}
