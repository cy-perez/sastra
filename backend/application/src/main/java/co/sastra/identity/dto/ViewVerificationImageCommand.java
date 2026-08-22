package co.sastra.identity.dto;

import co.sastra.identity.model.SellerVerificationId;
import co.sastra.identity.model.UserId;
import co.sastra.identity.model.VerificationImage;
import org.jspecify.annotations.Nullable;

/**
 * Ver una de las tres imagenes de una solicitud. Solo el rol de moderacion.
 *
 * @param moderador quien mira. Sale del token: es lo que queda en la bitacora
 * @param motivo lo que declara al mirar. Opcional, y nunca contiene el dato mirado
 */
public record ViewVerificationImageCommand(
        UserId moderador,
        SellerVerificationId verificacion,
        VerificationImage imagen,
        @Nullable String motivo) {}
