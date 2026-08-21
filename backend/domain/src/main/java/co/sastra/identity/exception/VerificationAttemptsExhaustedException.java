package co.sastra.identity.exception;

import co.sastra.shared.error.DomainException;
import co.sastra.shared.error.ErrorCode;

/**
 * RN-014: se agotaron los tres intentos y el siguiente exige revision manual.
 *
 * <p>La regla dice «maximo tres intentos; el cuarto exige revision manual», y aqui
 * se aplica al pie de la letra: al cuarto no se deja reintentar solo. Como en esta
 * historia toda revision es manual, «exige revision manual» solo puede querer decir
 * que hace falta que una persona intervenga para desbloquear, y ese mecanismo
 * todavia no existe: hoy el moderador lo resuelve por fuera.
 *
 * <p>Queda anotado porque es la interpretacion de una regla ambigua, no una
 * deduccion: si la intencion era permitir el cuarto envio marcandolo, cambia el
 * comportamiento y hay que cambiar esta clase.
 */
public final class VerificationAttemptsExhaustedException extends DomainException {

    private static final long serialVersionUID = 1L;

    public VerificationAttemptsExhaustedException(int intentos) {
        super(
                ErrorCode.SELLER_VERIFICATION_ATTEMPTS_EXHAUSTED,
                "La verificacion ya lleva " + intentos + " intentos y RN-014 permite tres");
    }
}
