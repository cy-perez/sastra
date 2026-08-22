package co.sastra.identity.dto;

import co.sastra.identity.model.RejectionReason;
import co.sastra.identity.model.SellerVerificationId;
import co.sastra.identity.model.UserId;
import org.jspecify.annotations.Nullable;

/**
 * Rechazar una verificacion con un motivo de la lista cerrada. Criterio 7 de HU-002.
 *
 * @param nota texto libre opcional que <strong>viaja a la persona rechazada</strong>.
 *     Nunca lleva informacion judicial ni datos de un tercero: es la regla que
 *     acompana al motivo generico REQUIREMENTS_NOT_MET, y no la puede imponer un tipo
 */
public record RejectVerificationCommand(
        UserId moderador,
        SellerVerificationId verificacion,
        RejectionReason motivo,
        @Nullable String nota) {}
