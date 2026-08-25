package co.sendik.identity.persistence;

import co.sendik.identity.model.Consent;
import co.sendik.identity.model.ConsentDocument;
import co.sendik.identity.model.ConsentId;
import co.sendik.identity.model.UserId;
import co.sendik.identity.port.out.ConsentRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Adaptador de persistencia de la evidencia de consentimiento (Ley 1581 de 2012). */
@Repository
public class JdbcConsentRepository implements ConsentRepository {

    private final JdbcClient jdbc;

    public JdbcConsentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void guardarTodos(List<Consent> consentimientos) {
        for (Consent consentimiento : consentimientos) {
            jdbc.sql("""
                            INSERT INTO consents (id, user_id, document, version, accepted_at, ip_hash)
                            VALUES (:id, :usuario, :documento, :version, :aceptado, :ipHash)
                            """)
                    .param("id", consentimiento.id().value())
                    .param("usuario", consentimiento.userId().value())
                    .param("documento", consentimiento.document().name())
                    .param("version", consentimiento.version())
                    .param("aceptado", Timestamp.from(consentimiento.acceptedAt()))
                    .param("ipHash", consentimiento.ipHash())
                    .update();
        }
    }

    /**
     * Criterio 22: la evidencia de a que documentos dijo que si, con su version.
     *
     * <p>La IP hasheada se lee igual porque el modelo la tiene, pero no sale de
     * aqui: el caso de uso la descarta al armar el archivo. Un hash no le dice nada
     * a quien recibe sus datos (docs/operacion/datos-personales.md).
     */
    @Override
    public List<Consent> listarDe(UserId usuario) {
        return jdbc.sql("""
                        SELECT id, user_id, document, version, accepted_at, ip_hash
                        FROM consents
                        WHERE user_id = :usuario
                        ORDER BY accepted_at DESC
                        """)
                .param("usuario", usuario.value())
                .query(JdbcConsentRepository::mapear)
                .list();
    }

    private static Consent mapear(ResultSet fila, int numeroDeFila) throws SQLException {
        return new Consent(
                new ConsentId(fila.getObject("id", UUID.class)),
                new UserId(fila.getObject("user_id", UUID.class)),
                ConsentDocument.valueOf(fila.getString("document")),
                fila.getString("version"),
                fila.getTimestamp("accepted_at").toInstant(),
                fila.getString("ip_hash"));
    }
}
