package co.sendik.identity.persistence;

import co.sendik.identity.model.Email;
import co.sendik.identity.model.TokenPurpose;
import co.sendik.identity.model.UserId;
import co.sendik.identity.model.VerificationToken;
import co.sendik.identity.model.VerificationTokenId;
import co.sendik.identity.port.out.VerificationTokenRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Adaptador de persistencia de los tokens de un solo uso. */
@Repository
public class JdbcVerificationTokenRepository implements VerificationTokenRepository {

    private final JdbcClient jdbc;

    public JdbcVerificationTokenRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void guardar(VerificationToken token) {
        jdbc.sql("""
                        INSERT INTO verification_tokens
                            (id, user_id, purpose, token_hash, expires_at, used_at, created_at, new_email)
                        VALUES (:id, :usuario, :proposito, :hash, :caduca, :usado, :creado, :correoNuevo)
                        """)
                .param("id", token.id().value())
                .param("usuario", token.userId().value())
                .param("proposito", token.purpose().name())
                .param("hash", token.tokenHash())
                .param("caduca", Timestamp.from(token.expiresAt()))
                .param("usado", token.usedAt() == null ? null : Timestamp.from(token.usedAt()))
                .param("creado", Timestamp.from(token.createdAt()))
                // Solo los tokens de cambio de correo lo llevan; la restriccion de
                // la tabla lo exige en un sentido y en el otro.
                .param(
                        "correoNuevo",
                        token.purpose() == TokenPurpose.EMAIL_CHANGE
                                ? token.newEmail().value()
                                : null)
                .update();
    }

    @Override
    public void actualizar(VerificationToken token) {
        jdbc.sql("UPDATE verification_tokens SET used_at = :usado WHERE id = :id")
                .param("usado", token.usedAt() == null ? null : Timestamp.from(token.usedAt()))
                .param("id", token.id().value())
                .update();
    }

    @Override
    public Optional<VerificationToken> buscarPorHash(String tokenHash) {
        return jdbc.sql("""
                        SELECT id, user_id, purpose, token_hash, expires_at, used_at, created_at, new_email
                        FROM verification_tokens
                        WHERE token_hash = :hash
                        """)
                .param("hash", tokenHash)
                .query(JdbcVerificationTokenRepository::mapear)
                .optional();
    }

    @Override
    public int contarEmitidosDesde(UserId usuario, TokenPurpose proposito, Instant desde) {
        return jdbc.sql("""
                        SELECT count(*)
                        FROM verification_tokens
                        WHERE user_id = :usuario AND purpose = :proposito AND created_at >= :desde
                        """)
                .param("usuario", usuario.value())
                .param("proposito", proposito.name())
                .param("desde", Timestamp.from(desde))
                .query(Integer.class)
                .single();
    }

    private static VerificationToken mapear(ResultSet fila, int numeroDeFila) throws SQLException {
        Timestamp usado = fila.getTimestamp("used_at");
        String correoNuevo = fila.getString("new_email");

        return VerificationToken.rehidratar(
                new VerificationTokenId(fila.getObject("id", UUID.class)),
                new UserId(fila.getObject("user_id", UUID.class)),
                TokenPurpose.valueOf(fila.getString("purpose")),
                fila.getString("token_hash"),
                fila.getTimestamp("expires_at").toInstant(),
                usado == null ? null : usado.toInstant(),
                fila.getTimestamp("created_at").toInstant(),
                correoNuevo == null ? null : new Email(correoNuevo));
    }
}
