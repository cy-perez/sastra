package co.sendik.identity.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * La decision de rechazar. Criterio 7 de HU-002.
 *
 * <p>El motivo es obligatorio y de la lista cerrada; la nota es libre y opcional.
 *
 * <p><strong>La nota viaja a la persona rechazada y nunca lleva informacion judicial ni
 * datos de un tercero.</strong> Es texto libre, asi que esa regla no la puede imponer una
 * anotacion: la impone quien revisa, y esta escrita en HU-002 y en el glosario para que no
 * se pierda. Lo unico que se comprueba aqui es que quepa.
 */
public record RejectVerificationRequest(
        @NotBlank String reason, @Nullable @Size(max = 500) String note) {}
