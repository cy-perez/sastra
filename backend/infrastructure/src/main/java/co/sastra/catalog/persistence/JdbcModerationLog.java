package co.sastra.catalog.persistence;

import co.sastra.catalog.model.ListingId;
import co.sastra.catalog.model.ModerationAction;
import co.sastra.catalog.model.ModeratorId;
import co.sastra.catalog.port.out.ModerationLog;
import co.sastra.shared.id.Uuid7;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * El rastro de cada decision de moderacion. RN-045.
 *
 * <p>Solo inserta. No hay actualizar ni borrar, y esa ausencia es la funcionalidad:
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
            @Nullable String nota) {

        jdbc.sql("""
                        INSERT INTO moderation_events (id, listing_id, actor_id, action, reason, notes)
                        VALUES (:id, :publicacion, :actor, :accion, :motivo, :nota)
                        """)
                .param("id", Uuid7.nuevo())
                .param("publicacion", publicacion.value())
                .param("actor", actor.value())
                .param("accion", accion.name())
                .param("motivo", motivo)
                .param("nota", nota)
                .update();
    }
}
