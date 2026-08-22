package co.sastra.identity.persistence;

import co.sastra.identity.model.SellerVerificationId;
import co.sastra.identity.model.UserId;
import co.sastra.identity.model.VerificationAccess;
import co.sastra.identity.port.out.VerificationAccessLog;
import co.sastra.shared.id.Uuid7;
import java.sql.Timestamp;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * La bitacora de accesos, sobre la tabla que crea {@code V8}.
 *
 * <p>Solo inserta. No hay actualizacion ni borrado, y la ausencia es la garantia: una
 * bitacora que se puede editar no sirve de bitacora. Lo unico que borra filas de aqui
 * es la cascada al eliminar la verificacion, y eso viene del cierre de cuenta.
 */
@Repository
public class JdbcVerificationAccessLog implements VerificationAccessLog {

    private final JdbcClient jdbc;

    public JdbcVerificationAccessLog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void registrar(
            SellerVerificationId verificacion,
            UserId actor,
            VerificationAccess accion,
            @Nullable String motivo,
            Instant ahora) {
        jdbc.sql("""
                        INSERT INTO verification_access_log (id, verification_id, actor_id, action, reason, created_at)
                        VALUES (:id, :verificacion, :actor, :accion, :motivo, :cuando)
                        """)
                .param("id", Uuid7.nuevo())
                .param("verificacion", verificacion.value())
                .param("actor", actor.value())
                .param("accion", accion.name())
                .param("motivo", motivo)
                .param("cuando", Timestamp.from(ahora))
                .update();
    }
}
