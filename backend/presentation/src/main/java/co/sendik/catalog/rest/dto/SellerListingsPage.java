package co.sendik.catalog.rest.dto;

import java.util.List;

/**
 * Las publicaciones propias, por pagina.
 *
 * <p>Por numero de pagina y no por cursor, que es la excepcion que contrato-api.md admite
 * para los listados administrativos acotados: es lo suyo, no el catalogo publico, y el
 * orden no cambia bajo los pies de quien lo mira. El catalogo publico, cuando llegue, si
 * va por cursor.
 */
public record SellerListingsPage(List<ListingResponse> items, int page, int size) {}
