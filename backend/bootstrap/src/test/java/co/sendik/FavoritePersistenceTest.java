package co.sendik;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.sendik.catalog.dto.FavoriteCursor;
import co.sendik.catalog.dto.FavoritedListing;
import co.sendik.catalog.model.BuyerId;
import co.sendik.catalog.model.Category;
import co.sendik.catalog.model.CategoryId;
import co.sendik.catalog.model.Color;
import co.sendik.catalog.model.Condition;
import co.sendik.catalog.model.Description;
import co.sendik.catalog.model.Favorite;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.model.ListingId;
import co.sendik.catalog.model.MeasurementGroup;
import co.sendik.catalog.model.MeasurementKind;
import co.sendik.catalog.model.Measurements;
import co.sendik.catalog.model.ModeratorId;
import co.sendik.catalog.model.Product;
import co.sendik.catalog.model.ProductId;
import co.sendik.catalog.model.ProductImage;
import co.sendik.catalog.model.ProductImageId;
import co.sendik.catalog.model.SellerId;
import co.sendik.catalog.model.ShippingDimensions;
import co.sendik.catalog.model.Size;
import co.sendik.catalog.model.SizeSystem;
import co.sendik.catalog.model.Title;
import co.sendik.catalog.port.out.Categories;
import co.sendik.catalog.port.out.Favorites;
import co.sendik.catalog.port.out.ListingRepository;
import co.sendik.shared.file.FileKey;
import co.sendik.shared.file.ImageContentType;
import co.sendik.shared.file.ImageDimensions;
import co.sendik.shared.money.Money;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * El adaptador de favoritos, contra PostgreSQL 17 real. HU-011.
 *
 * <p>Lo que se prueba aqui y no se puede probar en otro sitio: que la unicidad del par la
 * sostiene la tabla y no un {@code if}, que el {@code JOIN} filtra por estado como manda
 * RN-071, y que el cursor compara la pareja entera. Las pruebas de {@code FavoritosTest}
 * pasan contra un doble en memoria que ordena igual; estas comprueban que el SQL lo hace
 * de verdad.
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(PostgresTestContainer.class)
class FavoritePersistenceTest {

    private static final Instant AHORA = Instant.parse("2026-09-02T15:00:00Z");

    private final Favorites favoritos;
    private final ListingRepository publicaciones;
    private final Categories categorias;
    private final JdbcClient jdbc;

    FavoritePersistenceTest(
            Favorites favoritos, ListingRepository publicaciones, Categories categorias, JdbcClient jdbc) {
        this.favoritos = favoritos;
        this.publicaciones = publicaciones;
        this.categorias = categorias;
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------- escritura

    @Test
    void deberia_guardar_y_leer_un_favorito() {
        BuyerId quien = nuevoComprador();
        Listing publicada = publicada();

        favoritos.guardar(Favorite.reconstruir(quien, publicada.id(), AHORA));

        assertThat(favoritos.existe(quien, publicada.id())).isTrue();
    }

    /**
     * Criterio 4, y la razon de que la clave primaria sea el par. Sin la restriccion,
     * esto insertaria dos filas y la lista mostraria la misma publicacion dos veces.
     */
    @Test
    void deberia_ser_idempotente_por_la_clave_primaria_criterio_4() {
        BuyerId quien = nuevoComprador();
        Listing publicada = publicada();

        favoritos.guardar(Favorite.reconstruir(quien, publicada.id(), AHORA));

        assertThatCode(() ->
                        favoritos.guardar(Favorite.reconstruir(quien, publicada.id(), AHORA.plus(Duration.ofHours(1)))))
                .doesNotThrowAnyException();

        assertThat(cuantasFilas(quien)).isEqualTo(1);
    }

    /**
     * Y la segunda escritura no toca la fecha. Con {@code DO UPDATE} en vez de
     * {@code DO NOTHING}, un reintento de red moveria el favorito a la cabeza de la lista.
     */
    @Test
    void no_deberia_mover_la_fecha_al_repetir_la_marca() {
        BuyerId quien = nuevoComprador();
        Listing publicada = publicada();

        favoritos.guardar(Favorite.reconstruir(quien, publicada.id(), AHORA));
        favoritos.guardar(Favorite.reconstruir(quien, publicada.id(), AHORA.plus(Duration.ofHours(1))));

        assertThat(favoritos.todosDe(quien))
                .singleElement()
                .extracting(Favorite::marcadoEn)
                .isEqualTo(AHORA);
    }

    @Test
    void deberia_quitar_el_favorito_y_no_fallar_al_repetirlo() {
        BuyerId quien = nuevoComprador();
        Listing publicada = publicada();
        favoritos.guardar(Favorite.reconstruir(quien, publicada.id(), AHORA));

        favoritos.quitar(quien, publicada.id());
        assertThatCode(() -> favoritos.quitar(quien, publicada.id())).doesNotThrowAnyException();

        assertThat(favoritos.existe(quien, publicada.id())).isFalse();
    }

    /** La misma publicacion guardada por dos personas son dos filas, no un conflicto. */
    @Test
    void deberia_admitir_que_dos_personas_guarden_la_misma_publicacion() {
        BuyerId una = nuevoComprador();
        BuyerId otra = nuevoComprador();
        Listing publicada = publicada();

        favoritos.guardar(Favorite.reconstruir(una, publicada.id(), AHORA));
        favoritos.guardar(Favorite.reconstruir(otra, publicada.id(), AHORA));

        assertThat(cuantasFilas(una)).isEqualTo(1);
        assertThat(cuantasFilas(otra)).isEqualTo(1);
    }

    /**
     * Criterio 4, **de verdad**: dos escrituras simultaneas sobre el mismo par.
     *
     * <p>La prueba secuencial de arriba pasaria igual con un {@code if (existe) return;}
     * delante del {@code INSERT}, asi que no demuestra lo que su nombre dice. Lo que hay
     * que demostrar es lo que el puerto, el caso de uso, la migracion y la historia repiten
     * en cinco sitios: que entre esa lectura y la escritura cabe la peticion de la otra
     * pestana, y que lo unico que decide entonces es la clave primaria.
     *
     * <p>Los dos hilos escriben a la vez, cada uno con su conexion —cada uno abre la suya
     * al pedirla al {@code JdbcClient}—, y se sueltan con la misma barrera para que se
     * pisen de verdad. Ninguno puede fallar y tiene que quedar una fila.
     */
    @Test
    void deberia_ser_idempotente_entre_dos_escrituras_simultaneas_criterio_4() throws Exception {
        BuyerId quien = nuevoComprador();
        Listing publicada = publicada();
        Favorite favorito = Favorite.reconstruir(quien, publicada.id(), AHORA);

        CountDownLatch salida = new CountDownLatch(1);
        List<Throwable> fallos = Collections.synchronizedList(new ArrayList<>());

        Runnable escribir = () -> {
            try {
                salida.await();
                favoritos.guardar(favorito);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                fallos.add(e);
            }
        };

        Thread una = new Thread(escribir);
        Thread otra = new Thread(escribir);
        una.start();
        otra.start();
        salida.countDown();
        una.join();
        otra.join();

        assertThat(fallos).isEmpty();
        assertThat(cuantasFilas(quien)).isEqualTo(1);
    }

    /**
     * Las dos claves foraneas rechazan lo que no existe.
     *
     * <p>El doble en memoria no las tiene, asi que alli se puede guardar un favorito de una
     * cuenta inventada. Es la clase de divergencia que estas pruebas existen para cazar.
     */
    @Test
    void deberia_rechazar_un_favorito_de_alguien_o_de_algo_que_no_existe() {
        BuyerId quien = nuevoComprador();
        Listing publicada = publicada();

        assertThatThrownBy(() ->
                        favoritos.guardar(Favorite.reconstruir(new BuyerId(UUID.randomUUID()), publicada.id(), AHORA)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> favoritos.guardar(Favorite.reconstruir(quien, ListingId.nuevo(), AHORA)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Y la fila sobrevive a que la cuenta se anonimice.
     *
     * <p>De eso depende que las claves foraneas no lleven {@code ON DELETE CASCADE}: cerrar
     * una cuenta no borra su fila de {@code users}, la vacia. Si algun dia el cierre pasara
     * a borrar de verdad, esta prueba se cae y avisa de que hay que decidir que pasa con
     * estas filas —que hoy las borra el propio cierre, explicitamente—.
     */
    @Test
    void deberia_conservar_la_fila_cuando_la_cuenta_se_anonimiza() {
        BuyerId quien = nuevoComprador();
        Listing publicada = publicada();
        favoritos.guardar(Favorite.reconstruir(quien, publicada.id(), AHORA));

        jdbc.sql("UPDATE users SET status = 'CLOSED', email = :correo WHERE id = :id")
                .param("correo", quien.value() + "@anonimo.invalid")
                .param("id", quien.value())
                .update();

        assertThat(cuantasFilas(quien)).isEqualTo(1);
    }

    // ------------------------------------------------------------- la lista

    /** RN-071: el JOIN filtra por estado, y por eso el criterio 13 no necesita codigo. */
    @Test
    void no_deberia_devolver_lo_que_dejo_de_estar_publicado_RN_071() {
        BuyerId quien = nuevoComprador();
        Listing publicada = publicada();
        favoritos.guardar(Favorite.reconstruir(quien, publicada.id(), AHORA));

        publicaciones.guardar(publicada.pausar(AHORA));

        assertThat(favoritos.publicadasDe(quien, null, 24)).isEmpty();
        assertThat(cuantasFilas(quien)).isEqualTo(1);
    }

    /** Criterio 14: y vuelve sola cuando la publicacion vuelve. Misma consulta. */
    @Test
    void deberia_devolverla_cuando_se_republica_criterio_14() {
        BuyerId quien = nuevoComprador();
        Listing publicada = publicada();
        favoritos.guardar(Favorite.reconstruir(quien, publicada.id(), AHORA));

        Listing pausada = publicaciones.guardar(publicada.pausar(AHORA));
        publicaciones.guardar(pausada.reanudar(AHORA));

        assertThat(favoritos.publicadasDe(quien, null, 24))
                .extracting(par -> par.publicacion().id())
                .containsExactly(publicada.id());
    }

    /** RN-070: la lista es de quien la marco y de nadie mas. */
    @Test
    void no_deberia_devolver_los_favoritos_de_otra_persona_RN_070() {
        BuyerId quien = nuevoComprador();
        BuyerId ajena = nuevoComprador();
        Listing publicada = publicada();
        favoritos.guardar(Favorite.reconstruir(ajena, publicada.id(), AHORA));

        assertThat(favoritos.publicadasDe(quien, null, 24)).isEmpty();
    }

    /** Criterio 11: ordena por la fecha del gesto, no por la de publicacion. */
    @Test
    void deberia_ordenar_por_lo_marcado_mas_recientemente_criterio_11() {
        BuyerId quien = nuevoComprador();
        Listing vieja = publicadaEn(AHORA.minus(Duration.ofDays(30)));
        Listing nueva = publicadaEn(AHORA);

        favoritos.guardar(Favorite.reconstruir(quien, nueva.id(), AHORA));
        favoritos.guardar(Favorite.reconstruir(quien, vieja.id(), AHORA.plus(Duration.ofMinutes(5))));

        assertThat(favoritos.publicadasDe(quien, null, 24))
                .extracting(par -> par.publicacion().id())
                .containsExactly(vieja.id(), nueva.id());
    }

    /** La fecha que sale es la del favorito, que es con la que se arma el cursor. */
    @Test
    void deberia_devolver_la_fecha_del_gesto_y_no_la_de_publicacion() {
        BuyerId quien = nuevoComprador();
        Listing publicada = publicadaEn(AHORA.minus(Duration.ofDays(30)));
        Instant cuandoLoGuardo = AHORA.minus(Duration.ofHours(2));

        favoritos.guardar(Favorite.reconstruir(quien, publicada.id(), cuandoLoGuardo));

        assertThat(favoritos.publicadasDe(quien, null, 24))
                .singleElement()
                .extracting(FavoritedListing::marcadoEn)
                .isEqualTo(cuandoLoGuardo);
    }

    /**
     * Criterio 12, y la prueba que solo se puede hacer aqui: <strong>todos marcados en el
     * mismo instante</strong>. Si la consulta ordenara solo por {@code created_at}, el
     * orden entre iguales seria indefinido y el segundo tramo repetiria o se saltaria
     * filas. Es la comparacion de pareja lo que se esta comprobando.
     */
    @Test
    void deberia_recorrer_la_lista_sin_repetir_ni_saltar_con_todo_marcado_a_la_vez() {
        BuyerId quien = nuevoComprador();
        for (int i = 0; i < 5; i++) {
            favoritos.guardar(Favorite.reconstruir(quien, publicada().id(), AHORA));
        }

        List<FavoritedListing> primero = favoritos.publicadasDe(quien, null, 2);
        List<FavoritedListing> segundo = favoritos.publicadasDe(quien, cursorTras(primero), 2);
        List<FavoritedListing> tercero = favoritos.publicadasDe(quien, cursorTras(segundo), 2);

        List<ListingId> recorridas = List.of(primero, segundo, tercero).stream()
                .flatMap(List::stream)
                .map(par -> par.publicacion().id())
                .toList();

        assertThat(recorridas).hasSize(5).doesNotHaveDuplicates();
    }

    /** La tarjeta necesita la toma frontal, y llega con la publicacion (RN-016). */
    @Test
    void deberia_traer_la_toma_frontal_de_cada_publicacion() {
        BuyerId quien = nuevoComprador();
        Listing publicada = publicada();
        favoritos.guardar(Favorite.reconstruir(quien, publicada.id(), AHORA));

        assertThat(favoritos.publicadasDe(quien, null, 24))
                .singleElement()
                .satisfies(par -> assertThat(par.publicacion().images())
                        .singleElement()
                        .satisfies(imagen -> assertThat(imagen.position()).isZero()));
    }

    // ------------------------------------------------------------- datos personales

    /** La descarga entrega tambien lo que la lista esconde. */
    @Test
    void deberia_exportar_lo_que_ya_no_esta_publicado() {
        BuyerId quien = nuevoComprador();
        Listing publicada = publicada();
        favoritos.guardar(Favorite.reconstruir(quien, publicada.id(), AHORA));
        publicaciones.guardar(publicada.archivar(AHORA));

        assertThat(favoritos.publicadasDe(quien, null, 24)).isEmpty();
        assertThat(favoritos.todosDe(quien))
                .singleElement()
                .extracting(Favorite::publicacion)
                .isEqualTo(publicada.id());
    }

    @Test
    void deberia_borrar_solo_los_de_esa_persona() {
        BuyerId quien = nuevoComprador();
        BuyerId otra = nuevoComprador();
        Listing publicada = publicada();
        favoritos.guardar(Favorite.reconstruir(quien, publicada.id(), AHORA));
        favoritos.guardar(Favorite.reconstruir(otra, publicada.id(), AHORA));

        favoritos.borrarTodosDe(quien);

        assertThat(cuantasFilas(quien)).isZero();
        assertThat(cuantasFilas(otra)).isEqualTo(1);
    }

    // ------------------------------------------------------------- datos

    private static FavoriteCursor cursorTras(List<FavoritedListing> tramo) {
        FavoritedListing ultima = tramo.getLast();
        return new FavoriteCursor(ultima.marcadoEn(), ultima.publicacion().id());
    }

    private long cuantasFilas(BuyerId quien) {
        return jdbc.sql("SELECT count(*) FROM favorites WHERE user_id = :quien")
                .param("quien", quien.value())
                .query(Long.class)
                .single();
    }

    /** Una cuenta real: favorites.user_id apunta a users. */
    private UUID nuevoUsuario() {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO users (id, email, display_name, birth_date, status)
                        VALUES (:id, :correo, 'Alguien de prueba', DATE '1990-01-01', 'ACTIVE')
                        """).param("id", id).param("correo", id + "@ejemplo.co").update();
        return id;
    }

    private BuyerId nuevoComprador() {
        return new BuyerId(nuevoUsuario());
    }

    private Listing publicada() {
        return publicadaEn(AHORA);
    }

    private Listing publicadaEn(Instant cuando) {
        Listing enRevision = publicaciones.guardar(borradorConTomas().enviarARevision(cuando));
        return publicaciones.guardar(enRevision.aprobar(new ModeratorId(nuevoUsuario()), cuando));
    }

    private Listing borradorConTomas() {
        Listing resultado = Listing.crearBorrador(ListingId.nuevo(), producto(), AHORA);

        for (int posicion = 0; posicion < ProductImage.TOMAS_DE_LA_SECUENCIA; posicion++) {
            resultado = resultado.conImagen(
                    ProductImage.toma(
                            ProductImageId.nuevo(),
                            new FileKey("productos/" + UUID.randomUUID() + ".jpg"),
                            posicion,
                            new ImageDimensions(900, 1200),
                            120_000L,
                            ImageContentType.JPEG),
                    AHORA);
        }
        return resultado;
    }

    private Product producto() {
        Category camisas = categoriaPorSlug("camisas-y-blusas");

        Map<MeasurementKind, BigDecimal> valores = new EnumMap<>(MeasurementKind.class);
        MeasurementGroup.TOP.obligatorias().forEach(medida -> valores.put(medida, new BigDecimal("50.0")));

        return Product.crear(
                ProductId.nuevo(),
                new SellerId(nuevoUsuario()),
                camisas,
                new Title("Camisa de lino color hueso"),
                new Description("Usada dos veces."),
                null,
                Condition.LIKE_NEW,
                new Size(SizeSystem.ALPHA, "M"),
                new Measurements(valores),
                Color.BEIGE,
                Money.dePesos(185_000),
                new ShippingDimensions(600, new BigDecimal("30.0"), new BigDecimal("20.0"), new BigDecimal("10.0")),
                null,
                null);
    }

    private Category categoriaPorSlug(String slug) {
        UUID id = jdbc.sql("SELECT id FROM categories WHERE slug = :s")
                .param("s", slug)
                .query(UUID.class)
                .single();

        return categorias.buscar(new CategoryId(id)).orElseThrow();
    }
}
