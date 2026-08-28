package co.sendik.catalog.persistence;

import co.sendik.catalog.dto.CategoryView;
import co.sendik.catalog.model.Category;
import co.sendik.catalog.model.CategoryId;
import co.sendik.catalog.model.MeasurementGroup;
import co.sendik.catalog.model.SizeSystem;
import co.sendik.catalog.port.out.Categories;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
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

    /**
     * Donde se publica, colgando de esta. HU-009, criterios 9 y 10.
     *
     * <p>Una sola consulta y no dos: la categoria puede ser hoja o familia, y preguntar
     * primero cual es para decidir que consultar despues seria un viaje de mas para
     * responder lo mismo. El {@code OR} cubre los dos casos y el arbol tiene dos niveles,
     * asi que no hay recursion que hacer.
     *
     * <p><strong>Las dos condiciones de {@code active} son necesarias.</strong> La de la
     * fila descarta la categoria retirada; la del padre descarta la hoja cuya familia
     * entera se retiro, que es el mismo cuidado que ya tiene {@code arbolActivo} y por el
     * mismo motivo: una hoja colgada de una familia inactiva no se ofrece en ningun sitio,
     * asi que tampoco puede aparecer en el catalogo.
     *
     * <p>Devuelve solo hojas. Una familia no es un sitio donde publicar (glosario), asi
     * que incluirla en el filtro no traeria nada y solo alargaria el {@code IN}.
     */
    @Override
    public List<CategoryId> publicablesBajo(CategoryId id) {
        return jdbc.sql("""
                        SELECT hija.id
                        FROM categories hija
                        JOIN categories padre ON padre.id = hija.parent_id
                        WHERE padre.active
                          AND hija.active
                          AND (hija.id = :id OR hija.parent_id = :id)
                        ORDER BY hija.position
                        """).param("id", id.value()).query(UUID.class).list().stream()
                .map(CategoryId::new)
                .toList();
    }

    /**
     * El arbol activo, en dos pasos y una sola consulta.
     *
     * <p>Una consulta que trae todo y se arma en memoria, en vez de una por familia: son
     * treinta y siete filas y el arbol tiene dos niveles por definicion. Un recorrido
     * recursivo aqui seria complejidad para un problema que no existe.
     *
     * <p>El orden sale de la columna {@code position}, que es la que decide en que orden
     * se le ofrecen al vendedor. Sin ella el desplegable cambiaria de orden entre
     * peticiones.
     */
    @Override
    public List<CategoryView> arbolActivo() {
        List<Fila> filas = jdbc.sql("""
                        SELECT id, slug, parent_id, name_es, name_en, size_systems,
                               measurement_group, allows_used, position
                        FROM categories
                        WHERE active
                        ORDER BY position
                        """).query(JdbcCategories::fila).list();

        Map<UUID, String> slugsDeFamilia =
                filas.stream().filter(f -> f.padre() == null).collect(Collectors.toMap(Fila::id, Fila::slug));

        Map<String, List<CategoryView>> hijasPorFamilia = new LinkedHashMap<>();
        for (Fila hija : filas) {
            if (hija.padre() == null) {
                continue;
            }
            String familia = slugsDeFamilia.get(hija.padre());

            // Una hija cuya familia esta inactiva no se ofrece: la familia entera se
            // retiro, y colgarla de la nada la dejaria elegible sin donde encajar.
            if (familia != null) {
                hijasPorFamilia
                        .computeIfAbsent(familia, cualquiera -> new ArrayList<>())
                        .add(hija.aVista(familia, List.of()));
            }
        }

        return filas.stream()
                .filter(f -> f.padre() == null)
                .map(familia -> familia.aVista(null, hijasPorFamilia.getOrDefault(familia.slug(), List.of())))
                .toList();
    }

    /** Fila cruda: el mapeo a vista necesita saber antes quien es familia de quien. */
    private record Fila(
            UUID id,
            String slug,
            @Nullable UUID padre,
            String nombreEs,
            String nombreEn,
            Set<SizeSystem> sistemas,
            @Nullable MeasurementGroup grupo,
            boolean admiteUsado) {

        CategoryView aVista(@Nullable String familiaSlug, List<CategoryView> hijas) {
            return new CategoryView(
                    new CategoryId(id),
                    slug,
                    nombreEs,
                    nombreEn,
                    familiaSlug,
                    sistemas,
                    grupo == null ? Set.of() : grupo.obligatorias(),
                    admiteUsado,
                    hijas);
        }
    }

    private static Fila fila(ResultSet fila, int numero) throws SQLException {
        String grupo = fila.getString("measurement_group");

        return new Fila(
                fila.getObject("id", UUID.class),
                fila.getString("slug"),
                fila.getObject("parent_id", UUID.class),
                fila.getString("name_es"),
                fila.getString("name_en"),
                sistemas(fila.getArray("size_systems")),
                grupo == null ? null : MeasurementGroup.valueOf(grupo),
                fila.getBoolean("allows_used"));
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
