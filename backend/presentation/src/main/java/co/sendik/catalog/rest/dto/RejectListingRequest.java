package co.sendik.catalog.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Cuerpo del rechazo y de la retirada. Criterios 22 y 31.
 *
 * <p>El motivo es obligatorio y de lista cerrada: RN-022 no admite rechazar sin decir por
 * que. La nota es libre y opcional, y tiene tope porque va entera dentro de un correo.
 */
public record RejectListingRequest(
        @NotBlank String reason, @Nullable @Size(max = 500) String note) {}
