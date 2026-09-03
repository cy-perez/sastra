package co.sendik.identity.rest.dto;

import java.util.List;

/**
 * La bandeja del moderador, por pagina.
 *
 * <p>Por numero de pagina y no por cursor: contrato-api.md reserva el cursor para el
 * catalogo publico, donde entra contenido constantemente y una pagina fija repetiria y se
 * saltaria elementos, y admite pagina y tamano en los listados administrativos acotados.
 * Este lo es.
 *
 * <p>Es la misma forma que {@code PendingListingsPage} mas {@code hasMore}, y esa
 * diferencia es deliberada: la cola de publicaciones todavia no se pagina en ninguna
 * pantalla, asi que hoy no tiene a quien mentirle. Cuando se pagine, lleva el mismo campo.
 *
 * @param hasMore si detras de esta pagina queda al menos una solicitud. Lo dice el
 *     servidor y no se deduce de que {@code items} venga lleno: con un total multiplo
 *     exacto de {@code size}, deducirlo ofrece un «Siguiente» hacia una pagina vacia
 */
public record PendingVerificationsPage(List<PendingVerificationResponse> items, int page, int size, boolean hasMore) {}
