package co.sastra;

import static org.assertj.core.api.Assertions.assertThat;

import co.sastra.shared.config.AppProperties;
import co.sastra.shared.config.CommissionProperties;
import co.sastra.shared.config.CompanyProperties;
import co.sastra.shared.config.FeatureFlags;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifica que el cableado completo levanta: los cinco modulos, la base de datos
 * y la configuracion obligatoria.
 *
 * <p>Es una de las pocas pruebas con {@code @SpringBootTest} que este proyecto
 * admite, y vive en {@code bootstrap} porque es el unico modulo que conoce a
 * todos (docs/arquitectura/pruebas.md).
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(PostgresTestContainer.class)
class ApplicationContextTest {

    private final AppProperties app;
    private final CompanyProperties company;
    private final CommissionProperties commission;
    private final FeatureFlags features;

    ApplicationContextTest(
            AppProperties app, CompanyProperties company, CommissionProperties commission, FeatureFlags features) {
        this.app = app;
        this.company = company;
        this.commission = commission;
        this.features = features;
    }

    @Test
    void deberia_exponer_la_configuracion_obligatoria_ya_validada() {
        assertThat(app.baseUrl()).isNotNull();
        assertThat(app.apiBaseUrl()).isNotNull();
        assertThat(app.supportEmail()).isNotBlank();
        assertThat(app.corsAllowedOrigins()).isNotEmpty();
        assertThat(company.name()).isNotBlank();
        assertThat(company.taxId()).isNotBlank();
        assertThat(commission.rate()).isNotNull();
    }

    @Test
    void deberia_tener_apagada_toda_funcionalidad_posterior_a_la_fase_1() {
        assertThat(features.sellerVerification()).isFalse();
        assertThat(features.publishing()).isFalse();
        assertThat(features.checkout()).isFalse();
        assertThat(features.search()).isFalse();
        assertThat(features.spinViewer()).isFalse();
    }
}
