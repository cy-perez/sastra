package co.sendik.catalog.rest.dto;

import java.time.Instant;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Una publicacion en la bandeja del moderador. HU-008, criterio 1.
 *
 * <p>Una fila, no la publicacion entera: la bandeja sirve para elegir cual abrir, y para
 * eso bastan el titulo, el precio, cuanto lleva esperando y si algo pide mirarla con mas
 * cuidado. Lo demas —las ocho tomas, las medidas, la descripcion— llega al abrir el
 * detalle, que ya existe en {@code GET /api/v1/listings/&#123;id&#125;} y responde la
 * forma completa a un moderador.
 *
 * <p>Devolver el agregado entero por cada fila cargaria ocho imagenes por publicacion
 * para pintar una lista, y la primera pantalla es justo la que mas veces se abre.
 *
 * @param waitingSince cuando entro a revision, no cuando se toco por ultima vez. Es la
 *     columna {@code submitted_at} de V12: con {@code updated_at}, cambiar el precio de
 *     algo que espera turno reiniciaria la cuenta en pantalla
 * @param attentionReasons por que pide mirarse con mas cuidado (RN-020). Aqui si viaja el
 *     motivo, al contrario que en la lista del vendedor, donde solo se dice que la
 *     publicacion necesita atencion: el motivo es para quien revisa, y anunciarselo al
 *     vendedor antes de que nadie lo mire lo invita a cambiar el precio para esquivar la
 *     revision
 * @param coverUrl la toma frontal, para reconocer la publicacion sin abrirla. Nula si no
 *     esta: una publicacion puede llegar a revision con el archivo perdido por un fallo de
 *     despliegue, y eso no la hace indecidible
 * @param own si la publicacion es de quien mira la bandeja. RN-063 le prohibe decidir
 *     sobre lo suyo, y sin este dato la pantalla no puede avisarlo antes de que lo intente.
 *     Es un booleano y no el identificador del vendedor: responde lo unico que la pantalla
 *     necesita sin decir de quien es cada fila
 */
public record PendingListingResponse(
        String id,
        String title,
        MoneyPayload price,
        Instant waitingSince,
        boolean requiresAttention,
        Set<String> attentionReasons,
        @Nullable String coverUrl,
        boolean own) {}
