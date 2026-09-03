package co.sendik.catalog.rest.dto;

import java.util.List;

/**
 * La cola del moderador, por pagina.
 *
 * <p>Por numero de pagina y no por cursor: contrato-api.md reserva el cursor para el
 * catalogo publico, donde entra contenido constantemente y una pagina fija repetiria y se
 * saltaria elementos, y admite pagina y tamano en los listados administrativos acotados.
 * Este lo es.
 *
 * @param hasMore si detras de esta pagina queda al menos una publicacion. Lo dice el
 *     servidor y no se deduce de que {@code items} venga lleno: con un total multiplo
 *     exacto de {@code size}, deducirlo ofrece un «Siguiente» hacia una pagina vacia
 */
public record PendingListingsPage(List<PendingListingResponse> items, int page, int size, boolean hasMore) {}
