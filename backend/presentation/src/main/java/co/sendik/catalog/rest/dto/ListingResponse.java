package co.sendik.catalog.rest.dto;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * La publicacion como la ve su dueno o un moderador.
 *
 * <p>Lleva la cocina de la moderacion: por que esta marcada, con que motivo la
 * rechazaron y en que version va. Quien no es ninguno de los dos recibe
 * {@link PublicListingResponse}, que no lleva nada de eso.
 *
 * <p>{@code requiredShots} sale calculado y no se deduce en el cliente: son ocho, o
 * cuatro si es tecnologia sellada (RN-065), y esa regla es del dominio.
 *
 * @param sellerId de quien es la publicacion. **Nulo para un moderador que no es el
 *     dueno**, y es deliberado: la cola omite este campo para no ser de paso una lista de
 *     quien vende que, y dejarlo aqui deshacia esa proteccion con una peticion por fila.
 *     Quien si lo recibe es el dueno, que ya sabe que es suya. Para una publicacion
 *     visible el dato es publico de todos modos y viaja en {@link PublicListingResponse}
 * @param own si la publicacion es de quien pregunta, y solo tiene sentido para un
 *     moderador: RN-063 le prohibe decidir sobre lo suyo. Nulo cuando quien pregunta es el
 *     dueno, que ya sabe que es suya. Viaja aqui y no solo en la fila de la cola porque el
 *     detalle se abre tambien por su direccion directa, sin pasar por la bandeja: sin esto,
 *     un moderador que recarga sobre su propia publicacion ve los dos botones y se entera
 *     al pulsar, que es lo que el criterio 12 existe para evitar
 */
public record ListingResponse(
        String id,
        @Nullable String sellerId,
        String status,
        ProductResponse product,
        List<ListingImageResponse> images,
        int requiredShots,
        boolean requiresAttention,
        Set<String> attentionReasons,
        @Nullable String rejectionReason,
        @Nullable String rejectionNote,
        @Nullable Instant publishedAt,
        Instant createdAt,
        Instant updatedAt,
        long version,
        @Nullable Boolean own) {}
