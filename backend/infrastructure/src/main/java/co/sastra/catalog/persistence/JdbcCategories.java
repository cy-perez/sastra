package co.sastra.catalog.persistence;

import co.sastra.catalog.model.Category;
import co.sastra.catalog.model.CategoryId;
import co.sastra.catalog.model.MeasurementGroup;
import co.sastra.catalog.model.SizeSystem;
import co.sastra.catalog.port.out.Categories;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Lectura del arbol sembrado por la migracion V9.
 *
 * <p>Solo lectura, porque el arbol lo cambia una migracion y nada mas. El dia que
 * exista un panel de categorias, lo que se agregue aqui es escritura, y con ella la
 * comprobacion de profundidad que hoy sostiene el hecho de que solo V9 escribe en esta
 * tabla.
 */
@Repository
public class JdbcCategories implements Categories {

    private final JdbcClient jdbc;

    public JdbcCategories(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Category> buscar(CategoryId id) {
        return jdbc.sql("""
                        SELECT id, slug, parent_id, size_systems, measurement_group, allows_used, active
                        FROM categories
                        WHERE id = :id
                        """)
                .param("id", id.value())
                .query(JdbcCategories::filaACategoria)
                .optional();
    }

    private static Category filaACategoria(ResultSet fila, int numero) throws SQLException {
        UUID padre = fila.getObject("parent_id", UUID.class);
        String grupo = fila.getString("measurement_group");

        return new Category(
                new CategoryId(fila.getObject("id", UUID.class)),
                fila.getString("slug"),
                padre == null ? null : new CategoryId(padre),
                sistemas(fila.getArray("size_systems")),
                grupo == null ? null : MeasurementGroup.valueOf(grupo),
                fila.getBoolean("allows_used"),
                fila.getBoolean("active"));
    }

    /** El orden se conserva porque es el que la interfaz ofrece al vendedor. */
    private static Set<SizeSystem> sistemas(Array columna) throws SQLException {
        if (columna == null) {
            return Set.of();
        }
        return Arrays.stream((String[]) columna.getArray())
                .map(SizeSystem::valueOf)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }
}
