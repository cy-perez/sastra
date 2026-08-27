package co.sendik.catalog.rest.dto;

import java.util.List;

/**
 * La cola del moderador, por pagina.
 *
 * <p>Por numero de pagina y no por cursor: contrato-api.md reserva el cursor para el
 * catalogo publico, donde entra contenido constantemente y una pagina fija repetiria y se
 * saltaria elementos, y admite pagina y tamano en los listados administrativos acotados.
 * Este lo es.
 */
public record PendingListingsPage(List<PendingListingResponse> items, int page, int size) {}
