package co.sendik.catalog.rest.dto;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * La publicacion como la ve cualquiera. Solo existe para lo que esta publicado.
 *
 * <p><strong>Es una forma aparte y no la misma con campos en nulo.</strong> Con una sola
 * clase, el dia que alguien agregue un campo de moderacion se publica solo, sin que
 * ninguna prueba lo note. Lo que no esta aqui no se puede filtrar por descuido: no hay
 * campo donde meterlo.
 *
 * <p>Fuera quedan la version, las marcas de atencion, el motivo y la nota del rechazo y
 * las fechas de moderacion. El estado tampoco hace falta: si esta aqui, es que esta
 * publicada.
 */
public record PublicListingResponse(
        String id,
        String sellerId,
        ProductResponse product,
        List<ListingImageResponse> images,
        @Nullable Instant publishedAt) {}
