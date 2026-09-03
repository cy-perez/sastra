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
 * <p>Es la misma forma que {@code PendingListingsPage}, y a proposito: son las dos colas
 * de la misma pantalla y quien las consume no tiene por que aprender dos contratos.
 */
public record PendingVerificationsPage(List<PendingVerificationResponse> items, int page, int size) {}
