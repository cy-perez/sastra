package co.sendik;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * El esquema lo gobierna Flyway y nadie mas. Esta prueba corre las migraciones
 * contra PostgreSQL 17 real, que es la unica forma de saber que funcionan.
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(PostgresTestContainer.class)
class FlywayMigrationsTest {

    private final JdbcClient jdbc;

    FlywayMigrationsTest(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Test
    void deberia_aplicar_todas_las_migraciones_sin_ninguna_fallida() {
        long fallidas = jdbc.sql("SELECT count(*) FROM flyway_schema_history WHERE success = false")
                .query(Long.class)
                .single();
        long aplicadas = jdbc.sql("SELECT count(*) FROM flyway_schema_history WHERE success = true")
                .query(Long.class)
                .single();

        assertThat(fallidas).isZero();
        assertThat(aplicadas).isPositive();
    }

    @Test
    void deberia_dejar_disponible_la_extension_citext_que_normaliza_el_correo() {
        boolean existe = jdbc.sql("SELECT exists(SELECT 1 FROM pg_extension WHERE extname = 'citext')")
                .query(Boolean.class)
                .single();

        assertThat(existe).isTrue();
    }
}
