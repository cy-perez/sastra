package co.sastra;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL 17 real para las pruebas de aplicacion.
 *
 * <p>H2 esta prohibido en este proyecto: se comporta distinto a PostgreSQL, y una
 * migracion que funciona en H2 y falla en produccion es exactamente el error que
 * estas pruebas existen para evitar (docs/arquitectura/pruebas.md).
 */
@TestConfiguration(proxyBeanMethods = false)
class PostgresTestContainer {

    private static final String IMAGEN = "postgres:17-alpine";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse(IMAGEN));
    }
}
