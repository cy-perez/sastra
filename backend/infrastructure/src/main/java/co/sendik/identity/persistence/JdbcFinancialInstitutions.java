package co.sendik.identity.persistence;

import co.sendik.identity.model.BankCode;
import co.sendik.identity.port.out.FinancialInstitutions;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * El catalogo de entidades financieras, sobre la tabla que siembra {@code V7}.
 *
 * <p>Consulta y nada mas: agregar o desactivar una entidad es una migracion, no una
 * pantalla. Cuando exista un panel administrativo —Fase 4— esto crecera; hoy
 * inventarle un {@code guardar} seria codigo sin quien lo llame.
 */
@Repository
public class JdbcFinancialInstitutions implements FinancialInstitutions {

    private final JdbcClient jdbc;

    public JdbcFinancialInstitutions(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Ordenadas por nombre: es el orden en que alguien busca su banco en una lista. */
    @Override
    public List<FinancialInstitution> activas() {
        return jdbc.sql("SELECT code, name, kind FROM financial_institutions WHERE active ORDER BY name")
                .query((fila, numero) -> new FinancialInstitution(
                        fila.getString("code"), fila.getString("name"), "WALLET".equals(fila.getString("kind"))))
                .list();
    }

    @Override
    public boolean estaActiva(BankCode entidad) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM financial_institutions WHERE code = :codigo AND active)")
                .param("codigo", entidad.value())
                .query(Boolean.class)
                .single();
    }
}
