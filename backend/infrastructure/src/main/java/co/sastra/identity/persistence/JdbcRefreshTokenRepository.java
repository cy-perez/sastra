package co.sastra.identity.persistence;

import co.sastra.identity.model.RefreshToken;
import co.sastra.identity.model.RefreshTokenId;
import co.sastra.identity.model.TokenFamilyId;
import co.sastra.identity.model.UserId;
import co.sastra.identity.port.out.RefreshTokenRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Adaptador de persistencia de los tokens de refresco. */
@Repository
public class JdbcRefreshTokenRepository implements RefreshTokenRepository {

    private static final String SELECT_BASE = """
            SELECT id, user_id, family_id, token_hash, expires_at, revoked_at,
                   replaced_by, user_agent, ip_hash, created_at
            FROM refresh_tokens
            """;

    private final JdbcClient jdbc;

    public JdbcRefreshTokenRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void guardar(RefreshToken token) {
        jdbc.sql("""
                        INSERT INTO refresh_tokens
                            (id, user_id, family_id, token_hash, expires_at, revoked_at,
                             replaced_by, user_agent, ip_hash, created_at)
                        VALUES (:id, :usuario, :familia, :hash, :caduca, :revocado,
                                :reemplazo, :navegador, :ip, :creado)
                        """)
                .param("id", token.id().value())
                .param("usuario", token.userId().value())
                .param("familia", token.familyId().value())
                .param("hash", token.tokenHash())
                .param("caduca", Timestamp.from(token.expiresAt()))
                .param("revocado", token.revokedAt() == null ? null : Timestamp.from(token.revokedAt()))
                .param(
                        "reemplazo",
                        token.replacedBy() == null ? null : token.replacedBy().value())
                .param("navegador", token.userAgent())
                .param("ip", token.ipHash())
                .param("creado", Timestamp.from(token.createdAt()))
                .update();
    }

    /**
     * La rotacion completa en una sola sentencia, con una CTE que escribe.
     *
     * <p>PostgreSQL ejecuta cada sentencia dentro de su propia transaccion implicita,
     * asi que insertar el token nuevo y marcar el anterior aqui es atomico sin que
     * nadie tenga que abrir una transaccion alrededor. Eso es lo que permite que el
     * caso de uso no abra ninguna, y por tanto que la revocacion de una familia
     * reutilizada sobreviva a la excepcion que la acompana (criterio 15).
     *
     * <p>La clave ajena de {@code replaced_by} se comprueba al terminar la sentencia,
     * cuando la fila insertada por la CTE ya existe. El orden dentro del SQL no es
     * negociable por eso: la insercion tiene que ser la CTE y la actualizacion la
     * sentencia principal.
     *
     * <p>Solo se toca lo que puede cambiar despues de emitido, la revocacion y el
     * reemplazo. El hash y la caducidad no aparecen en el UPDATE, asi que no se pueden
     * cambiar por descuido.
     */
    @Override
    public void rotar(RefreshToken consumido, RefreshToken emitido) {
        jdbc.sql("""
                        WITH nuevo AS (
                            INSERT INTO refresh_tokens
                                (id, user_id, family_id, token_hash, expires_at, revoked_at,
                                 replaced_by, user_agent, ip_hash, created_at)
                            VALUES (:nuevoId, :usuario, :familia, :nuevoHash, :nuevaCaducidad, NULL,
                                    NULL, :navegador, :ip, :creado)
                            RETURNING id
                        )
                        UPDATE refresh_tokens
                        SET revoked_at  = :revocado,
                            replaced_by = (SELECT id FROM nuevo)
                        WHERE id = :consumidoId
                        """)
                .param("nuevoId", emitido.id().value())
                .param("usuario", emitido.userId().value())
                .param("familia", emitido.familyId().value())
                .param("nuevoHash", emitido.tokenHash())
                .param("nuevaCaducidad", Timestamp.from(emitido.expiresAt()))
                .param("navegador", emitido.userAgent())
                .param("ip", emitido.ipHash())
                .param("creado", Timestamp.from(emitido.createdAt()))
                .param("revocado", consumido.revokedAt() == null ? null : Timestamp.from(consumido.revokedAt()))
                .param("consumidoId", consumido.id().value())
                .update();
    }

    @Override
    public Optional<RefreshToken> buscarPorHash(String tokenHash) {
        return jdbc.sql(SELECT_BASE + " WHERE token_hash = :hash")
                .param("hash", tokenHash)
                .query(JdbcRefreshTokenRepository::mapear)
                .optional();
    }

    @Override
    public Optional<RefreshToken> buscarPorId(RefreshTokenId id) {
        return jdbc.sql(SELECT_BASE + " WHERE id = :id")
                .param("id", id.value())
                .query(JdbcRefreshTokenRepository::mapear)
                .optional();
    }

    /**
     * Un solo UPDATE sobre la familia, sin leer antes.
     *
     * <p>La condicion {@code revoked_at IS NULL} conserva la primera fecha de
     * revocacion de cada token, que es la que dice cuando se corto de verdad, y
     * hace la operacion idempotente: revocar dos veces la misma familia no reescribe
     * nada.
     */
    @Override
    public int revocarFamilia(TokenFamilyId familia, Instant ahora) {
        return jdbc.sql("""
                        UPDATE refresh_tokens
                        SET revoked_at = :ahora
                        WHERE family_id = :familia AND revoked_at IS NULL
                        """)
                .param("ahora", Timestamp.from(ahora))
                .param("familia", familia.value())
                .update();
    }

    /**
     * Criterio 20: todas las sesiones de la persona, en todos sus dispositivos.
     *
     * <p>Por {@code user_id} y no recorriendo familias: una consulta por familia
     * dejaria viva la que naciera entre la lectura y la escritura, que es justo el
     * hueco por el que sobreviviria la sesion que se quiere cortar.
     */
    @Override
    public int revocarTodasDe(UserId usuario, Instant ahora) {
        return jdbc.sql("""
                        UPDATE refresh_tokens
                        SET revoked_at = :ahora
                        WHERE user_id = :usuario AND revoked_at IS NULL
                        """)
                .param("ahora", Timestamp.from(ahora))
                .param("usuario", usuario.value())
                .update();
    }

    /**
     * Criterio 17: la cabeza viva de cada familia, de la mas reciente a la mas
     * antigua.
     *
     * <p>{@code replaced_by IS NULL} es lo que deja una sola fila por familia: el
     * token que todavia no se ha rotado. Sin esa condicion, una sesion de un mes
     * apareceria una vez por cada refresco.
     */
    @Override
    public List<RefreshToken> listarSesionesActivasDe(UserId usuario, Instant ahora) {
        return jdbc.sql(SELECT_BASE + """
                         WHERE user_id = :usuario
                           AND revoked_at IS NULL
                           AND replaced_by IS NULL
                           AND expires_at > :ahora
                         ORDER BY created_at DESC
                        """)
                .param("usuario", usuario.value())
                .param("ahora", Timestamp.from(ahora))
                .query(JdbcRefreshTokenRepository::mapear)
                .list();
    }

    /**
     * El {@code user_id} va en el WHERE y no en una comprobacion previa: entre
     * comprobar y escribir cabe un cambio, y aqui lo que se comprueba es de quien es
     * la sesion que se esta cerrando.
     */
    @Override
    public int revocarSesionDe(UserId usuario, TokenFamilyId familia, Instant ahora) {
        return jdbc.sql("""
                        UPDATE refresh_tokens
                        SET revoked_at = :ahora
                        WHERE user_id = :usuario AND family_id = :familia AND revoked_at IS NULL
                        """)
                .param("ahora", Timestamp.from(ahora))
                .param("usuario", usuario.value())
                .param("familia", familia.value())
                .update();
    }

    private static RefreshToken mapear(ResultSet fila, int numeroDeFila) throws SQLException {
        Timestamp revocado = fila.getTimestamp("revoked_at");
        UUID reemplazo = fila.getObject("replaced_by", UUID.class);

        return RefreshToken.rehidratar(
                new RefreshTokenId(fila.getObject("id", UUID.class)),
                new UserId(fila.getObject("user_id", UUID.class)),
                new TokenFamilyId(fila.getObject("family_id", UUID.class)),
                fila.getString("token_hash"),
                fila.getTimestamp("expires_at").toInstant(),
                revocado == null ? null : revocado.toInstant(),
                reemplazo == null ? null : new RefreshTokenId(reemplazo),
                fila.getString("user_agent"),
                fila.getString("ip_hash"),
                fila.getTimestamp("created_at").toInstant());
    }
}
