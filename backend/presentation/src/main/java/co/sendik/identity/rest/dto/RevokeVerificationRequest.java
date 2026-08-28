package co.sendik.identity.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * La decision de revocar un sello ya otorgado. RN-013 y RN-069.
 *
 * <p><strong>Es un cuerpo aparte del de rechazar aunque tenga la misma forma.</strong> Lo
 * que cambia no es la forma sino de donde sale el motivo: aqui es {@code RevocationReason}
 * y alli {@code RejectionReason}, que son dos listas cerradas distintas. Con un solo DTO,
 * el dia que una de las dos listas cambie no habria nada que impidiera mandar un valor de
 * la otra, y el borde aceptaria una cadena que el dominio no puede convertir.
 *
 * <p>La nota es libre, opcional y viaja a la persona. Vale lo mismo que en el rechazo:
 * nunca lleva informacion judicial ni datos de un tercero, y eso no lo impone una
 * anotacion sino quien revisa. Aqui solo se comprueba que quepa.
 */
public record RevokeVerificationRequest(
        @NotBlank String reason, @Nullable @Size(max = 500) String note) {}
