package co.sastra.identity.dto;

import co.sastra.identity.model.RejectionReason;
import co.sastra.identity.model.SellerVerificationId;
import co.sastra.identity.model.UserId;
import org.jspecify.annotations.Nullable;

/**
 * Revocar el sello de quien ya estaba verificado. RN-013.
 *
 * <p>Lleva motivo obligatorio como el rechazo: quitarle a alguien la capacidad de
 * vender sin dejar escrito por que es lo que hace imposible revisar la decision
 * despues.
 */
public record RevokeVerificationCommand(
        UserId moderador,
        SellerVerificationId verificacion,
        RejectionReason motivo,
        @Nullable String nota) {}
