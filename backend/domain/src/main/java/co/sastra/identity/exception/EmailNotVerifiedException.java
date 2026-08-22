package co.sastra.identity.exception;

import co.sastra.shared.error.DomainException;
import co.sastra.shared.error.ErrorCode;

/**
 * Criterio 1 de HU-002: para verificarse como vendedor hay que tener el correo
 * verificado.
 *
 * <p>Es lo mismo que RN-002 exige para publicar o comprar, aplicado a la puerta de
 * entrada del proceso. Codigo propio y no {@code AUTH_*} porque lo que la persona
 * tiene que hacer es distinto: aqui no ha fallado su credencial, le falta un paso que
 * ya tiene empezado.
 */
public final class EmailNotVerifiedException extends DomainException {

    private static final long serialVersionUID = 1L;

    public EmailNotVerifiedException() {
        super(ErrorCode.SELLER_EMAIL_NOT_VERIFIED, "La cuenta no tiene el correo verificado (criterio 1 de HU-002)");
    }
}
