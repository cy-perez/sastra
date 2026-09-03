package co.sendik;

import static org.assertj.core.api.Assertions.assertThat;

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
import co.sendik.identity.dto.CloseAccountCommand;
import co.sendik.identity.dto.RegisterUserCommand;
import co.sendik.identity.dto.UserDataExport;
import co.sendik.identity.model.Email;
import co.sendik.identity.model.UserId;
import co.sendik.identity.port.out.UserRepository;
import co.sendik.identity.usecase.CloseAccountUseCase;
import co.sendik.identity.usecase.ExportUserDataUseCase;
import co.sendik.identity.usecase.RegisterUserUseCase;
import co.sendik.shared.file.FileKey;
import co.sendik.shared.file.ImageContentType;
import co.sendik.shared.file.ImageDimensions;
import co.sendik.shared.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * Los favoritos como dato personal, con las dos mitades unidas. HU-011.
 *
 * <p><strong>Existe porque el cierre y la descarga estaban probados por mitades que no se
 * tocaban.</strong> `CloseAccountUseCaseTest` verifica sobre un simulador que se llama a
 * `borrarDe`; `FavoritosTest` y `FavoritePersistenceTest` verifican que `borrarTodosDe`
 * borra. En medio queda `CatalogUserFavorites`, el adaptador que cruza los dos contextos y
 * traduce entre `UserId` y `BuyerId`, y no lo probaba nada: si tradujera mal, las dos
 * mitades seguirian verdes y el cierre no borraria nada.
 *
 * <p>La historia lo pide con esas palabras: «hay que comprobar que el cierre los arrastra y
 * que la descarga de datos los incluye». Esto es lo que lo comprueba, contra PostgreSQL y
 * atravesando los dos contextos.
 */
@SpringBootTest(properties = "sendik.features.catalog=true")
@ActiveProfiles("local")
@Import(PostgresTestContainer.class)
class FavoritesPersonalDataTest {

    private static final Instant AHORA = Instant.parse("2026-09-02T15:00:00Z");

    private final Favorites favoritos;
    private final ListingRepository publicaciones;
    private final Categories categorias;
    private final CloseAccountUseCase cierre;
    private final ExportUserDataUseCase descarga;
    private final RegisterUserUseCase registro;
    private final UserRepository usuarios;
    private final JdbcClient jdbc;

    FavoritesPersonalDataTest(
            Favorites favoritos,
            ListingRepository publicaciones,
            Categories categorias,
            CloseAccountUseCase cierre,
            ExportUserDataUseCase descarga,
            RegisterUserUseCase registro,
            UserRepository usuarios,
            JdbcClient jdbc) {
        this.favoritos = favoritos;
        this.publicaciones = publicaciones;
        this.categorias = categorias;
        this.cierre = cierre;
        this.descarga = descarga;
        this.registro = registro;
        this.usuarios = usuarios;
        this.jdbc = jdbc;
    }

    /**
     * La descarga los trae, y trae los que la lista esconde.
     *
     * <p>RN-071 hace que un favorito cuya publicación se archivó deje de verse; el derecho a
     * conocer es sobre lo que se guarda, no sobre lo que se enseña.
     */
    @Test
    void deberia_incluir_los_favoritos_en_la_descarga_de_datos() {
        UserId quien = nuevaCuenta();
        Listing publicada = publicada();
        Listing archivada = publicada();

        favoritos.guardar(Favorite.reconstruir(new BuyerId(quien.value()), publicada.id(), AHORA));
        favoritos.guardar(Favorite.reconstruir(new BuyerId(quien.value()), archivada.id(), AHORA));
        publicaciones.guardar(archivada.archivar(AHORA));

        UserDataExport archivo = descarga.execute(quien);

        assertThat(archivo.favoritos())
                .extracting(UserDataExport.Favorito::publicacion)
                .containsExactlyInAnyOrder(
                        publicada.id().toString(), archivada.id().toString());
        assertThat(archivo.favoritos())
                .allSatisfy(favorito -> assertThat(favorito.marcadoEl()).isEqualTo(AHORA));
    }

    /**
     * El cierre los arrastra, de verdad y hasta la tabla.
     *
     * <p>Es el derecho de supresión de la Ley 1581 sobre un dato que dice qué le interesa a
     * una persona identificada (docs/operacion/datos-personales.md).
     */
    @Test
    void deberia_borrar_los_favoritos_al_cerrar_la_cuenta() {
        UserId quien = nuevaCuenta();
        UserId otra = nuevaCuenta();
        Listing publicada = publicada();

        favoritos.guardar(Favorite.reconstruir(new BuyerId(quien.value()), publicada.id(), AHORA));
        favoritos.guardar(Favorite.reconstruir(new BuyerId(otra.value()), publicada.id(), AHORA));

        cierre.execute(new CloseAccountCommand(quien, correoDe(quien)));

        assertThat(cuantasFilas(quien)).isZero();
        // Y no toca los de nadie más: son datos de otra persona sobre la misma publicación.
        assertThat(cuantasFilas(otra)).isEqualTo(1);
    }

    // ------------------------------------------------------------- datos

    private long cuantasFilas(UserId quien) {
        return jdbc.sql("SELECT count(*) FROM favorites WHERE user_id = :quien")
                .param("quien", quien.value())
                .query(Long.class)
                .single();
    }

    private String correoDe(UserId quien) {
        return usuarios.buscarPorId(quien).orElseThrow().email().value();
    }

    /**
     * Una cuenta de verdad, creada por su caso de uso.
     *
     * <p>Y no con un {@code INSERT} a mano como en las otras pruebas de persistencia: aquí
     * se cierra, y cerrar necesita una cuenta con todo lo que el cierre toca.
     */
    private UserId nuevaCuenta() {
        String correo = "favoritos-" + UUID.randomUUID() + "@ejemplo.co";

        registro.execute(new RegisterUserCommand(
                correo,
                "una-contrasena-larga-de-verdad",
                "Quien Guarda",
                LocalDate.of(1990, 3, 4),
                "es",
                true,
                true,
                "hash-de-ip"));

        return usuarios.buscarPorCorreo(new Email(correo)).orElseThrow().id();
    }

    private UUID nuevoUsuarioSuelto() {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO users (id, email, display_name, birth_date, status)
                        VALUES (:id, :correo, 'Alguien de prueba', DATE '1990-01-01', 'ACTIVE')
                        """).param("id", id).param("correo", id + "@ejemplo.co").update();
        return id;
    }

    private Listing publicada() {
        Listing borrador = conTomas(Listing.crearBorrador(ListingId.nuevo(), producto(), AHORA));
        Listing enRevision = publicaciones.guardar(borrador.enviarARevision(AHORA));

        return publicaciones.guardar(enRevision.aprobar(new ModeratorId(nuevoUsuarioSuelto()), AHORA));
    }

    private static Listing conTomas(Listing publicacion) {
        Listing resultado = publicacion;
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
        Map<MeasurementKind, BigDecimal> valores = new EnumMap<>(MeasurementKind.class);
        MeasurementGroup.TOP.obligatorias().forEach(medida -> valores.put(medida, new BigDecimal("50.0")));

        return Product.crear(
                ProductId.nuevo(),
                new SellerId(nuevoUsuarioSuelto()),
                categoriaPorSlug("camisas-y-blusas"),
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
