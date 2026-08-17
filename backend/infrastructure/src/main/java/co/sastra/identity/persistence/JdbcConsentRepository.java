package co.sastra.identity.persistence;

import co.sastra.identity.model.Consent;
import co.sastra.identity.port.out.ConsentRepository;
import java.sql.Timestamp;
import java.util.List;
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
}
