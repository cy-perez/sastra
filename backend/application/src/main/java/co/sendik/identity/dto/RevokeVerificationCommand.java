package co.sendik.identity.dto;

import co.sendik.identity.model.RevocationReason;
import co.sendik.identity.model.SellerVerificationId;
import co.sendik.identity.model.UserId;
import org.jspecify.annotations.Nullable;

/**
 * Revocar el sello de quien ya estaba verificado. RN-013.
 *
 * <p>Lleva motivo obligatorio como el rechazo: quitarle a alguien la capacidad de
 * vender sin dejar escrito por que es lo que hace imposible revisar la decision
 * despues.
 *
 * <p>El motivo es de {@link RevocationReason} y no de la lista del rechazo (RN-069). No
 * son intercambiables: aquella juzga una solicitud y esta se lo quita a alguien que ya
 * vende, y el valor elegido va en el correo que la persona recibe.
 */
public record RevokeVerificationCommand(
        UserId moderador,
        SellerVerificationId verificacion,
        RevocationReason motivo,
        @Nullable String nota) {}
