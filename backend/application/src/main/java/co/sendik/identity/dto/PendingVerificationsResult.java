package co.sendik.identity.dto;

import co.sendik.identity.model.SellerVerification;
import java.util.List;

/**
 * Una pagina de la bandeja del moderador, y si detras queda algo mas. HU-006.
 *
 * <p><strong>El «hay mas» lo contesta quien consulta, no quien pinta.</strong> Lo deducia
 * la pantalla de que la pagina viniera llena, y esa deduccion falla justo cuando el total
 * es multiplo exacto del tamano: con veinte pendientes y veinte por pagina, la bandeja
 * ofrece un «Siguiente» que lleva a una pagina vacia. Quien revisa pulsa, no encuentra
 * nada, y no puede saber si la cola se acabo o si algo se rompio.
 *
 * <p>Se resuelve pidiendo una fila mas de las que caben y devolviendola recortada, que es
 * lo que hace {@link co.sendik.identity.usecase.ListPendingVerificationsUseCase}. Contar
 * es la otra forma, y cuesta una consulta mas sobre la misma tabla en cada carga para
 * responder un si o un no.
 *
 * @param items las solicitudes de esta pagina, ya recortadas al tamano que se pidio
 * @param hayMas si detras de esta pagina queda al menos una solicitud
 */
public record PendingVerificationsResult(List<SellerVerification> items, boolean hayMas) {

    public PendingVerificationsResult {
        items = List.copyOf(items);
    }
}
