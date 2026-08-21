package co.sastra.identity.persistence;

import co.sastra.identity.model.Email;
import co.sastra.identity.port.out.LoginAttemptRecorder;
import co.sastra.shared.id.Uuid7;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Adaptador de auditoria de accesos.
 *
 * <p>El correo se guarda hasheado con SHA-256 sobre su forma normalizada, de modo
 * que dos intentos con el mismo correo escrito distinto caen en el mismo valor y se
 * pueden agrupar. No lleva sal a proposito: una sal por fila haria imposible
 * justamente eso, que es lo unico para lo que sirve esta tabla.
 *
 * <p>El hash no protege gran cosa frente a quien quiera comprobar si un correo
 * concreto aparece, porque puede calcularlo. Lo que evita es lo probable: que un
 * volcado de esta tabla de auditoria sea de por si una lista de direcciones
 * (docs/operacion/datos-personales.md).
 */
@Repository
public class JdbcLoginAttemptRecorder implements LoginAttemptRecorder {

    private final JdbcClient jdbc;

    public JdbcLoginAttemptRecorder(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void registrar(Email correo, @Nullable String ipHash, boolean exitoso, Instant ahora) {
        jdbc.sql("""
                        INSERT INTO login_attempts (id, email_hash, ip_hash, succeeded, created_at)
                        VALUES (:id, :correo, :ip, :exitoso, :cuando)
                        """)
                .param("id", Uuid7.nuevo())
                .param("correo", hashearCorreo(correo))
                .param("ip", ipHash)
                .param("exitoso", exitoso)
                .param("cuando", Timestamp.from(ahora))
                .update();
    }

    private static String hashearCorreo(Email correo) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(correo.value().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 es obligatorio en toda plataforma Java.
            throw new IllegalStateException("SHA-256 no esta disponible en esta JVM", e);
        }
    }
}
