package co.sendik.catalog.model;

import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Algo que le paso a una publicacion y quedo anotado. HU-013, RN-045.
 *
 * <p><strong>Es la forma de lectura de la bitacora, y por eso no lleva actor ni nota.</strong>
 * Las dos columnas existen en {@code moderation_events} y las dos se siguen escribiendo:
 * auditar exige saber quien decidio, y la nota se escribio para Sendik. Lo que no se hace
 * es devolverlas (RN-074). No estan aqui porque un tipo que las lleve las pone a un
 * {@code map} de distancia de salir en el JSON, y filtrar al borde es confiar en que nadie
 * escriba el campo de mas.
 *
 * <p><strong>Un motivo ausente no invalida el evento, ni siquiera donde deberia haberlo.</strong>
 * La tentacion es exigirlo para {@code REJECTED} —RN-022 lo manda, y la tabla tiene esa
 * misma restriccion—, pero esto lee filas que ya estan escritas: una sola fila vieja o
 * torcida haria estallar el rastro entero de esa publicacion, y quien vende se quedaria
 * sin ver tampoco las que si estan bien. La regla se hace cumplir al escribir, que es
 * donde sirve de algo; al leer se pinta lo que hay y no se inventa texto.
 */
public record ModerationEvent(
        ModerationAction action, @Nullable ListingRejectionReason reason, Instant occurredAt) {

    public ModerationEvent {
        Objects.requireNonNull(action, "La accion es obligatoria");
        Objects.requireNonNull(occurredAt, "La fecha del evento es obligatoria");
    }
}
