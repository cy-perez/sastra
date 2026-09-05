package co.sendik.catalog.rest.dto;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Lo que le paso a una publicacion, lo mas reciente primero. HU-013.
 *
 * <p><strong>No hay campo para quien decidio, y esa ausencia es el criterio 5.</strong> La
 * bitacora guarda {@code actor_id} porque auditar exige saber quien decidio; lo que no se
 * hace es devolverlo (RN-074). Una decision de moderacion es de Sendik, y ponerle nombre
 * convierte una discrepancia con la plataforma en una discrepancia con una persona. Tampoco
 * esta la nota interna, que se escribio para Sendik y no para quien vende.
 *
 * <p>Que no aparezcan aqui es la ultima de tres barreras, no la unica: tampoco viajan en el
 * tipo de dominio ni salen de la consulta SQL.
 *
 * <p><strong>Un objeto con una lista dentro y no la lista suelta.</strong> Una respuesta que
 * es un arreglo de primer nivel no admite agregarle nada despues -un total, un cursor si
 * algun dia se pagina- sin romper a todo cliente que la lea.
 */
public record ModerationHistoryResponse(List<ModerationEventResponse> events) {

    /**
     * Un paso del rastro.
     *
     * <p>La accion viaja como texto y no como la enumeracion del dominio, igual que el
     * estado en {@code SellerListingsSummaryResponse}: un DTO de la API no depende de
     * {@code model}, y quien traduce es {@code ModerationHistories}.
     *
     * <p>{@code reason} es nulo donde no lo hay -aprobar no lleva motivo, y enviar
     * tampoco- y tambien donde deberia haberlo y no esta. La pantalla pinta la fila igual
     * y no inventa texto.
     */
    public record ModerationEventResponse(
            String action, @Nullable String reason, Instant occurredAt) {}
}
