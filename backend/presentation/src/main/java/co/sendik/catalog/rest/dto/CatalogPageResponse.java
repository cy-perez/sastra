package co.sendik.catalog.rest.dto;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Un tramo del catalogo. HU-009, criterio 3.
 *
 * <p>La forma que contrato-api.md fija para los listados por cursor, y distinta a
 * proposito de la de la cola del moderador: aquella lleva {@code page} y {@code size}
 * porque es administrativa y acotada, esta lleva {@code nextCursor} y {@code hasMore}
 * porque entra contenido constantemente.
 *
 * <p>{@code nextCursor} es nulo en el ultimo tramo. Van los dos campos y no solo uno
 * porque el cliente los usa para cosas distintas: {@code hasMore} decide si pinta «ver
 * mas» y {@code nextCursor} es lo que manda al pulsarlo.
 *
 * <p>Cada elemento es la forma <strong>publica</strong> de la publicacion. No hay aqui
 * una forma reducida de tarjeta: la tarjeta usa un subconjunto de lo mismo, y una tercera
 * forma seria un tercer sitio donde revisar que no se filtre nada de moderacion.
 */
public record CatalogPageResponse(
        List<PublicListingResponse> items, @Nullable String nextCursor, boolean hasMore) {}
