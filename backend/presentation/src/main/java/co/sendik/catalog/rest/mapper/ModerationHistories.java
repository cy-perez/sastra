package co.sendik.catalog.rest.mapper;

import co.sendik.catalog.model.ListingRejectionReason;
import co.sendik.catalog.model.ModerationEvent;
import co.sendik.catalog.rest.dto.ModerationHistoryResponse;
import co.sendik.catalog.rest.dto.ModerationHistoryResponse.ModerationEventResponse;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Del rastro del dominio al de la API. HU-013.
 *
 * <p>Vive aqui y no como factoria dentro del propio DTO porque traducir exige leer las
 * enumeraciones del dominio, y un {@code rest.dto} no puede depender de {@code model}: lo
 * impide {@code ArchitectureTest}, y lo impide para que el dominio pueda renombrar una
 * accion sin que el contrato publico cambie por debajo.
 *
 * <p>No hay nada que filtrar aqui: {@link ModerationEvent} no trae actor ni nota. Si
 * hubiera que quitarlos en este punto, el criterio 5 dependeria de que nadie escribiera un
 * campo de mas en el DTO.
 */
public final class ModerationHistories {

    private ModerationHistories() {}

    /** Conserva el orden que trae el puerto: lo mas reciente primero. */
    public static ModerationHistoryResponse de(List<ModerationEvent> rastro) {
        return new ModerationHistoryResponse(rastro.stream()
                .map(evento -> new ModerationEventResponse(
                        evento.action().name(), nombreDe(evento.reason()), evento.occurredAt()))
                .toList());
    }

    private static @Nullable String nombreDe(@Nullable ListingRejectionReason motivo) {
        return motivo == null ? null : motivo.name();
    }
}
