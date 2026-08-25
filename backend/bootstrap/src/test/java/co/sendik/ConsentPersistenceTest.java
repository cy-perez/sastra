package co.sendik;

import static org.assertj.core.api.Assertions.assertThat;

import co.sendik.identity.model.Consent;
import co.sendik.identity.model.ConsentDocument;
import co.sendik.identity.model.UserId;
import co.sendik.identity.port.out.ConsentRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * La evidencia de consentimiento contra PostgreSQL 17 real. Ley 1581 de 2012.
 *
 * <p>Esto no se puede dar por bueno con simulaciones. Lo que la ley exige demostrar
 * anos despues es a que texto concreto dijo que si una persona, y eso depende de que
 * la fila sobreviva entera: el documento, su version y el instante. Un doble del
 * repositorio devolveria lo que se le guardo sin pasar por la columna, que es donde
 * de verdad se pierde un dato (docs/operacion/datos-personales.md).
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(PostgresTestContainer.class)
class ConsentPersistenceTest {

    private static final Instant AHORA = Instant.parse("2026-08-25T14:30:00Z");
    private static final String HASH_DE_IP = "b8c2f1a0e4d7";

    private final ConsentRepository consentimientos;
    private final JdbcClient jdbc;

    ConsentPersistenceTest(ConsentRepository consentimientos, JdbcClient jdbc) {
        this.consentimientos = consentimientos;
        this.jdbc = jdbc;
    }

    /**
     * Una fila por documento, no una por registro: una sola casilla para ambos no es
     * consentimiento valido y no se podria demostrar por separado.
     */
    @Test
    void deberia_guardar_los_dos_documentos_por_separado_ley_1581() {
        UserId persona = nuevoUsuario();

        consentimientos.guardarTodos(List.of(
                otorgado(persona, ConsentDocument.TERMS, "2026-01", AHORA),
                otorgado(persona, ConsentDocument.PRIVACY, "2026-03", AHORA)));

        assertThat(consentimientos.listarDe(persona))
                .extracting(Consent::document)
                .containsExactlyInAnyOrder(ConsentDocument.TERMS, ConsentDocument.PRIVACY);
    }

    /**
     * Criterio 22: la version es lo que hace demostrable el consentimiento. Si se
     * perdiera por el camino, dentro de dos anos nadie sabria a que texto dijo que si,
     * y la fila serviria de tan poco como no tenerla.
     */
    @Test
    void deberia_conservar_la_version_exacta_de_cada_documento_criterio_22() {
        UserId persona = nuevoUsuario();

        consentimientos.guardarTodos(List.of(
                otorgado(persona, ConsentDocument.TERMS, "2026-01", AHORA),
                otorgado(persona, ConsentDocument.PRIVACY, "2026-03", AHORA)));

        assertThat(consentimientos.listarDe(persona))
                .filteredOn(consentimiento -> consentimiento.document() == ConsentDocument.PRIVACY)
                .singleElement()
                .satisfies(privacidad -> {
                    assertThat(privacidad.version()).isEqualTo("2026-03");
                    assertThat(privacidad.acceptedAt()).isEqualTo(AHORA);
                    assertThat(privacidad.userId()).isEqualTo(persona);
                });
    }

    /** Del mas reciente al mas antiguo: es el orden en que se lee un historial. */
    @Test
    void deberia_devolver_del_mas_reciente_al_mas_antiguo() {
        UserId persona = nuevoUsuario();
        Instant elAnoPasado = AHORA.minusSeconds(365L * 24 * 3600);

        consentimientos.guardarTodos(List.of(
                otorgado(persona, ConsentDocument.TERMS, "2025-01", elAnoPasado),
                otorgado(persona, ConsentDocument.PRIVACY, "2026-03", AHORA)));

        assertThat(consentimientos.listarDe(persona))
                .extracting(Consent::version)
                .containsExactly("2026-03", "2025-01");
    }

    /**
     * La IP viaja hasheada y el repositorio la devuelve tal cual: leerla es su trabajo.
     * Quien la descarta es el caso de uso al armar el archivo de datos personales, que
     * es donde tiene sentido decidirlo. Si el adaptador la perdiera aqui, esa decision
     * dejaria de existir y la evidencia de que hubo una aceptacion desde algun sitio se
     * iria con ella.
     */
    @Test
    void deberia_devolver_el_hash_de_la_ip_y_dejar_el_descarte_al_caso_de_uso() {
        UserId persona = nuevoUsuario();

        consentimientos.guardarTodos(List.of(otorgado(persona, ConsentDocument.TERMS, "2026-01", AHORA)));

        assertThat(consentimientos.listarDe(persona))
                .singleElement()
                .satisfies(consentimiento -> assertThat(consentimiento.ipHash()).isEqualTo(HASH_DE_IP));
    }

    /** El hash es opcional en el modelo y la columna lo admite nulo. */
    @Test
    void deberia_admitir_un_consentimiento_sin_hash_de_ip() {
        UserId persona = nuevoUsuario();

        consentimientos.guardarTodos(List.of(new Consent(
                co.sendik.identity.model.ConsentId.nuevo(), persona, ConsentDocument.PRIVACY, "2026-03", AHORA, null)));

        assertThat(consentimientos.listarDe(persona))
                .singleElement()
                .satisfies(consentimiento -> assertThat(consentimiento.ipHash()).isNull());
    }

    /**
     * Lo que nunca puede pasar con datos personales: que el archivo de una persona
     * traiga la evidencia de otra.
     */
    @Test
    void no_deberia_mezclar_los_consentimientos_de_dos_personas() {
        UserId mia = nuevoUsuario();
        UserId ajena = nuevoUsuario();

        consentimientos.guardarTodos(List.of(otorgado(mia, ConsentDocument.TERMS, "2026-01", AHORA)));
        consentimientos.guardarTodos(List.of(
                otorgado(ajena, ConsentDocument.TERMS, "2026-01", AHORA),
                otorgado(ajena, ConsentDocument.PRIVACY, "2026-03", AHORA)));

        assertThat(consentimientos.listarDe(mia))
                .singleElement()
                .satisfies(consentimiento -> assertThat(consentimiento.userId()).isEqualTo(mia));
    }

    /** No haber aceptado nada todavia no es un error: es una lista vacia. */
    @Test
    void deberia_devolver_vacio_para_quien_no_tiene_consentimientos() {
        assertThat(consentimientos.listarDe(nuevoUsuario())).isEmpty();
    }

    // ------------------------------------------------------------------ apoyo

    private static Consent otorgado(UserId persona, ConsentDocument documento, String version, Instant cuando) {
        return Consent.otorgar(persona, documento, version, cuando, HASH_DE_IP);
    }

    /** Una cuenta real: consents.user_id apunta a users. */
    private UserId nuevoUsuario() {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO users (id, email, display_name, birth_date, status)
                        VALUES (:id, :correo, 'Persona de prueba', DATE '1990-01-01', 'ACTIVE')
                        """).param("id", id).param("correo", id + "@ejemplo.co").update();
        return new UserId(id);
    }
}
