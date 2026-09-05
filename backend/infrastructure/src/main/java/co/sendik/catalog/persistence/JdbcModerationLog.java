package co.sendik.catalog.persistence;

import co.sendik.catalog.model.ListingId;
import co.sendik.catalog.model.ListingRejectionReason;
import co.sendik.catalog.model.ModerationAction;
import co.sendik.catalog.model.ModerationEvent;
import co.sendik.catalog.model.ModeratorId;
import co.sendik.catalog.model.SellerId;
import co.sendik.catalog.port.out.ModerationLog;
import co.sendik.shared.id.Uuid7;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * El rastro de cada paso por moderacion. RN-045.
 *
 * <p>Solo inserta y lee. No hay actualizar ni borrar, y esa ausencia es la funcionalidad:
 * una bitacora que se puede reescribir no prueba nada. Que una publicacion se archive
 * despues no borra que se aprobo antes.
 */
@Repository
public class JdbcModerationLog implements ModerationLog {

    private final JdbcClient jdbc;

    public JdbcModerationLog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void registrar(
            ListingId publicacion,
            ModeratorId actor,
            ModerationAction accion,
            @Nullable String motivo,
            @Nullable String nota,
            Instant cuando) {

        jdbc.sql("""
                        INSERT INTO moderation_events
                            (id, listing_id, actor_id, action, reason, notes, created_at)
                        VALUES (:id, :publicacion, :actor, :accion, :motivo, :nota, :cuando)
                        """)
                .param("id", Uuid7.nuevo())
                .param("publicacion", publicacion.value())
                .param("actor", actor.value())
                .param("accion", accion.name())
                .param("motivo", motivo)
                .param("nota", nota)
                // Timestamp y no el Instant crudo, por lo mismo que abajo: pgjdbc no infiere
                // el tipo SQL de java.time.Instant.
                .param("cuando", Timestamp.from(cuando))
                .update();
    }

    /**
     * El envio a revision, que hace el vendedor.
     *
     * <p>La fecha la sella el dominio al entrar a {@code PENDING_REVIEW} y es la misma que
     * queda en {@code listings.submitted_at}. Ninguna fila de esta tabla se fecha ya con el
     * {@code now()} del motor: todo el rastro sale del mismo reloj, que es lo que hace que
     * su orden signifique algo.
     *
     * <p>El vendedor entra por {@code actor_id}, que es la columna de quien hizo esto. Es
     * la unica traduccion que hace falta entre los dos identificadores tipados, y vive aqui
     * -en {@code infrastructure}- porque es un detalle de como se guarda y no del modelo.
     */
    @Override
    public void registrarEnvio(ListingId publicacion, SellerId vendedor, Instant cuando) {
        jdbc.sql("""
                        INSERT INTO moderation_events (id, listing_id, actor_id, action, created_at)
                        VALUES (:id, :publicacion, :vendedor, 'SUBMITTED', :cuando)
                        """)
                .param("id", Uuid7.nuevo())
                .param("publicacion", publicacion.value())
                .param("vendedor", vendedor.value())
                // Timestamp y no el Instant crudo: pgjdbc no infiere el tipo SQL de
                // java.time.Instant y falla al preparar la sentencia. Es la misma
                // conversion que hace JdbcListingRepository con todas sus fechas.
                .param("cuando", Timestamp.from(cuando))
                .update();
    }

    /**
     * Lo que le paso a esa publicacion, lo mas reciente primero. HU-013.
     *
     * <p><strong>La consulta no nombra {@code actor_id} ni {@code notes}.</strong> Es el
     * criterio 5 y RN-074 puestos donde de verdad se cumplen: los dos datos existen en la
     * fila y se siguen escribiendo, pero no salen de la base. Traerlos y descartarlos
     * despues dejaria el criterio a merced de que nadie anada el campo al DTO.
     *
     * <p><strong>Desempata por {@code id} cuando dos eventos caen en el mismo instante.</strong>
     * Sin ese segundo criterio el orden entre ellos es indefinido y PostgreSQL puede
     * devolverlos distinto entre dos cargas de la misma pantalla. El identificador es
     * {@link Uuid7}, asi que ordena por el momento en que se escribio la fila y no al azar
     * (ADR-0015). Pasa mas de lo que parece: aprobar escribe la decision y el vendedor puede
     * haber reenviado en el mismo milisegundo.
     *
     * <p>El indice {@code moderation_events_listing} cubre exactamente este filtro y este
     * orden.
     */
    @Override
    public List<ModerationEvent> historial(ListingId publicacion) {
        return jdbc.sql("""
                        SELECT action, reason, created_at
                        FROM moderation_events
                        WHERE listing_id = :publicacion
                        ORDER BY created_at DESC, id DESC
                        """)
                .param("publicacion", publicacion.value())
                .query((fila, numero) -> new ModerationEvent(
                        ModerationAction.valueOf(fila.getString("action")),
                        motivoDe(fila.getString("reason")),
                        fila.getTimestamp("created_at").toInstant()))
                .list();
    }

    /**
     * El motivo, o nada.
     *
     * <p>Nulo es normal y no un dato que falte: aprobar no lleva motivo y enviar tampoco.
     * Rechazar y retirar si, y la restriccion de la tabla lo exige al escribir, que es donde
     * sirve de algo.
     */
    private static @Nullable ListingRejectionReason motivoDe(@Nullable String guardado) {
        return guardado == null ? null : ListingRejectionReason.valueOf(guardado);
    }
}
