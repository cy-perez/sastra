package co.sendik;

import static org.assertj.core.api.Assertions.assertThat;

import co.sendik.identity.model.BankCode;
import co.sendik.identity.port.out.FinancialInstitutions;
import co.sendik.identity.port.out.FinancialInstitutions.FinancialInstitution;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * El catalogo de entidades financieras contra PostgreSQL 17 real. HU-002.
 *
 * <p>Aqui el adaptador y los datos son la misma cosa: la lista la siembra {@code V7} y
 * lo unico que hay que comprobar es que la consulta la lee como la pantalla la
 * necesita. Con un doble no quedaria probado ni que la siembra corrio ni que
 * {@code kind} se traduce al booleano correcto, que es de lo que depende que el
 * formulario ofrezca cuenta de ahorros o deposito electronico.
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(PostgresTestContainer.class)
class FinancialInstitutionsPersistenceTest {

    private final FinancialInstitutions entidades;
    private final JdbcClient jdbc;

    FinancialInstitutionsPersistenceTest(FinancialInstitutions entidades, JdbcClient jdbc) {
        this.entidades = entidades;
        this.jdbc = jdbc;
    }

    @Test
    void deberia_ofrecer_las_entidades_que_sembro_la_migracion() {
        assertThat(entidades.activas())
                .extracting(FinancialInstitution::code)
                .contains("bancolombia", "davivienda", "nequi", "daviplata");
    }

    /**
     * BANK admite ahorros y corriente; WALLET solo deposito electronico. La distincion
     * no es cosmetica: con el tipo equivocado, el desembolso de la Fase 3 no llega.
     */
    @Test
    void deberia_distinguir_la_billetera_del_banco() {
        assertThat(unaDe("nequi").wallet()).isTrue();
        assertThat(unaDe("bancolombia").wallet()).isFalse();
    }

    /** El nombre visible sale de la tabla, que es donde vive: no se guarda en la fila del vendedor. */
    @Test
    void deberia_traer_el_nombre_visible_de_la_entidad() {
        assertThat(unaDe("davivienda").name()).isEqualTo("Banco Davivienda");
    }

    /**
     * Ordenadas por nombre, que es como alguien busca su banco en un desplegable. Se
     * comprueba con dos nombres sin tildes a proposito: el orden exacto de «Bogotá»
     * depende de la intercalacion de la base y no es lo que esta prueba decide.
     */
    @Test
    void deberia_devolverlas_ordenadas_por_nombre() {
        var nombres =
                entidades.activas().stream().map(FinancialInstitution::name).toList();

        assertThat(nombres.indexOf("Banco AV Villas")).isLessThan(nombres.indexOf("Bancolombia"));
    }

    @Test
    void deberia_reconocer_una_entidad_activa_del_catalogo() {
        assertThat(entidades.estaActiva(new BankCode("bancolombia"))).isTrue();
    }

    /** Un codigo inventado no existe. Sin esto, cualquier cadena con forma valida pasaria. */
    @Test
    void no_deberia_reconocer_un_codigo_que_no_esta_en_el_catalogo() {
        assertThat(entidades.estaActiva(new BankCode("banco-que-no-existe"))).isFalse();
    }

    /**
     * Una entidad que deja de operar se desactiva, no se borra: hay filas de vendedores
     * apuntando a ella. Desactivada tiene que dejar de ofrecerse y dejar de validar, o
     * el formulario seguiria aceptando una cuenta donde ya no llega el dinero.
     *
     * <p>La entidad se inserta aqui en lugar de desactivar una sembrada: el contenedor
     * es uno para toda la ejecucion, y tocar la siembra le cambiaria el catalogo a las
     * demas pruebas.
     */
    @Test
    void no_deberia_ofrecer_ni_validar_una_entidad_desactivada() {
        BankCode retirada = new BankCode("entidad-retirada-de-prueba");
        jdbc.sql("""
                        INSERT INTO financial_institutions (code, name, kind, active)
                        VALUES (:codigo, 'Entidad retirada de prueba', 'BANK', false)
                        """).param("codigo", retirada.value()).update();

        assertThat(entidades.estaActiva(retirada)).isFalse();
        assertThat(entidades.activas()).extracting(FinancialInstitution::code).doesNotContain(retirada.value());
    }

    private FinancialInstitution unaDe(String codigo) {
        return entidades.activas().stream()
                .filter(entidad -> entidad.code().equals(codigo))
                .findFirst()
                .orElseThrow();
    }
}
