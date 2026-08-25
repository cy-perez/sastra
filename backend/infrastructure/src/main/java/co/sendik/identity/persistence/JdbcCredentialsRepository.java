package co.sendik.identity.persistence;

import co.sendik.identity.model.PasswordHash;
import co.sendik.identity.model.UserCredentials;
import co.sendik.identity.model.UserId;
import co.sendik.identity.port.out.CredentialsRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Adaptador de persistencia de las credenciales y del contador de intentos.
 *
 * <p>La fila la crea {@link JdbcUserRepository} al registrar la cuenta: aqui no hay
 * insercion porque unas credenciales sin usuario no existen.
 */
@Repository
public class JdbcCredentialsRepository implements CredentialsRepository {

    private final JdbcClient jdbc;

    public JdbcCredentialsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UserCredentials> buscarPorUsuario(UserId usuario) {
        return jdbc.sql("""
                        SELECT user_id, password_hash, password_updated_at, failed_attempts, locked_until
                        FROM user_credentials
                        WHERE user_id = :usuario
                        """)
                .param("usuario", usuario.value())
                .query(JdbcCredentialsRepository::mapear)
                .optional();
    }

    /**
     * Solo el contador y el bloqueo. El hash no entra en este UPDATE: cambiarlo es
     * otra operacion, la de restablecer la contrasena, y mezclarlas permitiria que
     * un intento fallido reescribiera la credencial.
     */
    @Override
    public void actualizar(UserCredentials credenciales) {
        jdbc.sql("""
                        UPDATE user_credentials
                        SET failed_attempts = :intentos,
                            locked_until    = :bloqueo
                        WHERE user_id = :usuario
                        """)
                .param("intentos", credenciales.failedAttempts())
                .param(
                        "bloqueo",
                        credenciales.lockedUntil() == null ? null : Timestamp.from(credenciales.lockedUntil()))
                .param("usuario", credenciales.userId().value())
                .update();
    }

    /**
     * El unico UPDATE que toca {@code password_hash}.
     *
     * <p>Escribe tambien la fecha y limpia el contador y el bloqueo, porque los
     * cuatro son la misma decision: hay contrasena nueva, asi que lo que sabiamos de
     * la anterior deja de valer.
     */
    @Override
    public void cambiarContrasena(UserCredentials credenciales) {
        jdbc.sql("""
                        UPDATE user_credentials
                        SET password_hash       = :hash,
                            password_updated_at = :actualizada,
                            failed_attempts     = 0,
                            locked_until        = NULL
                        WHERE user_id = :usuario
                        """)
                .param("hash", credenciales.passwordHash().value())
                .param("actualizada", Timestamp.from(credenciales.passwordUpdatedAt()))
                .param("usuario", credenciales.userId().value())
                .update();
    }

    private static UserCredentials mapear(ResultSet fila, int numeroDeFila) throws SQLException {
        Timestamp bloqueo = fila.getTimestamp("locked_until");

        return UserCredentials.rehidratar(
                new UserId(fila.getObject("user_id", UUID.class)),
                new PasswordHash(fila.getString("password_hash")),
                fila.getTimestamp("password_updated_at").toInstant(),
                fila.getInt("failed_attempts"),
                bloqueo == null ? null : bloqueo.toInstant());
    }
}
