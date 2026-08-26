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
 */
public record ListingResponse(
        String id,
        String sellerId,
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
        long version) {}
