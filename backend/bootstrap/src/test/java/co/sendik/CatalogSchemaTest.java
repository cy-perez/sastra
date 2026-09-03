package co.sendik;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * El esquema de catalogo y el arbol sembrado. HU-007, rebanada A.
 *
 * <p>Comprueba contra PostgreSQL 17 real lo que ningun repaso a ojo garantiza: que
 * las restricciones de la migracion rechazan de verdad lo que dicen rechazar. Las
 * que se prueban aqui son las que sostienen una regla de negocio, no todas: una
 * restriccion que solo repite lo que el dominio ya valida se prueba en el dominio.
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(PostgresTestContainer.class)
class CatalogSchemaTest {

    private final JdbcClient jdbc;

    CatalogSchemaTest(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Test
    void deberia_sembrar_las_seis_familias_y_las_treinta_y_una_categorias() {
        long familias = contar("SELECT count(*) FROM categories WHERE parent_id IS NULL");
        long hojas = contar("SELECT count(*) FROM categories WHERE parent_id IS NOT NULL");

        assertThat(familias).isEqualTo(6);
        assertThat(hojas).isEqualTo(31);
    }

    @Test
    void deberia_dejar_toda_la_tecnologia_sin_permitir_lo_usado_RN_064() {
        List<String> tecnologiaQuePermiteUsado =
                jdbc.sql("""
                        SELECT hoja.slug
                        FROM categories hoja
                        JOIN categories familia ON familia.id = hoja.parent_id
                        WHERE familia.slug = 'tech' AND hoja.allows_used = true
                        """).query(String.class).list();

        assertThat(tecnologiaQuePermiteUsado).isEmpty();
    }

    @Test
    void deberia_dejar_toda_la_moda_permitiendo_lo_usado_RN_064() {
        List<String> modaQueNoPermiteUsado = jdbc.sql("""
                        SELECT hoja.slug
                        FROM categories hoja
                        JOIN categories familia ON familia.id = hoja.parent_id
                        WHERE familia.slug <> 'tech' AND hoja.allows_used = false
                        """).query(String.class).list();

        assertThat(modaQueNoPermiteUsado).isEmpty();
    }

    @Test
    void deberia_dar_a_cada_hoja_su_grupo_de_medida_y_al_menos_un_sistema_de_talla() {
        long incompletas = contar("""
                SELECT count(*) FROM categories
                WHERE parent_id IS NOT NULL
                  AND (measurement_group IS NULL OR cardinality(size_systems) = 0)
                """);

        assertThat(incompletas).isZero();
    }

    // Sin eje de genero, unos jeans se venden en las dos escalas. Es el caso que
    // obligo a que la columna fuera plural.
    @Test
    void deberia_admitir_dos_sistemas_de_talla_en_una_misma_categoria() {
        List<String> sistemasDeJeans = jdbc.sql("SELECT unnest(size_systems) FROM categories WHERE slug = 'jeans'")
                .query(String.class)
                .list();

        assertThat(sistemasDeJeans).containsExactlyInAnyOrder("WAIST_INCHES", "NUMERIC_CO");
    }

    @Test
    void deberia_impedir_un_tercer_nivel_de_arbol() {
        // Las familias no tienen padre y las hojas cuelgan de una familia. Que no haya
        // nietos es lo que mantiene el menu en dos niveles.
        long nietos = contar("""
                SELECT count(*)
                FROM categories nieto
                JOIN categories padre ON padre.id = nieto.parent_id
                WHERE padre.parent_id IS NOT NULL
                """);

        assertThat(nietos).isZero();
    }

    @Test
    void deberia_rechazar_una_publicacion_en_un_estado_que_nadie_definio() {
        assertThatThrownBy(() -> jdbc.sql("""
                        INSERT INTO listings (id, product_id, status)
                        VALUES (gen_random_uuid(), gen_random_uuid(), 'ESPERANDO')
                        """).update()).hasMessageContaining("listings_status_valid");
    }

    @Test
    void deberia_rechazar_una_imagen_de_referencia_con_angulo_RN_066() {
        assertThatThrownBy(() -> jdbc.sql("""
                        INSERT INTO product_images
                            (id, product_id, kind, object_key, position, angle_degrees, width, height, bytes, content_type)
                        VALUES
                            (gen_random_uuid(), gen_random_uuid(), 'REFERENCE', 'k', 0, 90, 900, 1200, 100, 'image/webp')
                        """).update()).hasMessageContaining("product_images_referencia_sin_angulo");
    }

    @Test
    void deberia_rechazar_una_toma_de_vendedor_con_angulo_que_no_es_multiplo_de_45_RN_017() {
        assertThatThrownBy(() -> jdbc.sql("""
                        INSERT INTO product_images
                            (id, product_id, kind, object_key, position, angle_degrees, width, height, bytes, content_type)
                        VALUES
                            (gen_random_uuid(), gen_random_uuid(), 'SELLER_SHOT', 'k', 0, 30, 900, 1200, 100, 'image/webp')
                        """).update()).hasMessageContaining("product_images_toma_de_vendedor");
    }

    @Test
    void deberia_crear_los_cuatro_indices_que_el_modelo_de_datos_exige() {
        List<String> indices = jdbc.sql("""
                        SELECT indexname FROM pg_indexes
                        WHERE tablename IN ('listings', 'products', 'product_images')
                        """).query(String.class).list();

        assertThat(indices)
                .contains(
                        "listings_status_published",
                        "products_seller",
                        "product_images_posicion_unica",
                        // V12, la cola del moderador. Parcial sobre PENDING_REVIEW: es la
                        // consulta de la bandeja escrita como indice.
                        "idx_listings_review_queue");
    }

    private long contar(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }

    /**
     * El indice de la lista de favoritos. HU-011, criterios 11 y 12.
     *
     * <p>{@code V16} lo justifica en veinte lineas como «la consulta escrita como indice»,
     * y sin esta prueba se podia borrar sin que fallara nada: la consulta seguiria dando el
     * resultado correcto, ordenando en memoria la lista entera de alguien antes de entregar
     * las primeras veinticuatro.
     *
     * <p>Se comprueban tambien las tres columnas y su orden, que es lo que lo hace servir
     * para esta consulta y no para otra.
     */
    @Test
    void deberia_tener_el_indice_de_la_lista_de_favoritos() {
        String definicion = jdbc.sql("SELECT indexdef FROM pg_indexes WHERE indexname = :nombre")
                .param("nombre", "idx_favorites_recent")
                .query(String.class)
                .single();

        assertThat(definicion).contains("user_id", "created_at DESC", "listing_id DESC");
    }

    /** Y la clave primaria compuesta, que es lo que sostiene la idempotencia del criterio 4. */
    @Test
    void deberia_tener_la_clave_primaria_sobre_el_par() {
        String definicion = jdbc.sql("""
                        SELECT pg_get_constraintdef(oid)
                        FROM pg_constraint
                        WHERE conrelid = 'favorites'::regclass AND contype = 'p'
                        """).query(String.class).single();

        assertThat(definicion).isEqualTo("PRIMARY KEY (user_id, listing_id)");
    }
}
