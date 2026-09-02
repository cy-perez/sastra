package co.sendik;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.sendik.catalog.model.Category;
import co.sendik.catalog.model.CategoryId;
import co.sendik.catalog.model.Color;
import co.sendik.catalog.model.Condition;
import co.sendik.catalog.model.Description;
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
import co.sendik.catalog.port.out.ListingRepository;
import co.sendik.identity.model.BirthDate;
import co.sendik.identity.model.DisplayName;
import co.sendik.identity.model.Email;
import co.sendik.identity.model.Role;
import co.sendik.identity.model.TokenFamilyId;
import co.sendik.identity.model.User;
import co.sendik.identity.model.UserId;
import co.sendik.identity.model.UserLocale;
import co.sendik.identity.model.UserStatus;
import co.sendik.identity.port.out.AccessTokenIssuer;
import co.sendik.shared.file.FileKey;
import co.sendik.shared.file.ImageContentType;
import co.sendik.shared.file.ImageDimensions;
import co.sendik.shared.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Quien puede hacer que con los favoritos. HU-011.
 *
 * <p><strong>Es la prueba que las de {@code presentation} no pueden hacer.</strong> Alli el
 * montaje es autonomo y no tiene cadena de filtros, asi que la autorizacion no existe y
 * todo responde 200. Y las de {@code application} tampoco: el 403 de RN-072 y el 404 de
 * RN-068 son excepciones de dominio hasta que alguien las traduce, y quien las traduce es
 * el manejador global, que solo existe con el contexto entero.
 *
 * <p>Lo que se verifica aqui es lo que hace privada una lista privada:
 *
 * <ul>
 *   <li>Las cuatro rutas exigen token. La lista es de quien pregunta y no hay forma de
 *       nombrar la de otra persona: el identificador sale del {@code sub} y no de la ruta.
 *   <li>Marcar lo propio responde 403 con su codigo, no 200 en silencio (RN-072).
 *   <li>Marcar lo que no esta publicado responde 404, igual que lo que no existe (RN-068).
 *   <li>Quitar es idempotente hasta en el borde: 204 aunque no hubiera nada que quitar.
 * </ul>
 */
@SpringBootTest(properties = "sendik.features.catalog=true")
@ActiveProfiles("local")
@Import(PostgresTestContainer.class)
class FavoritesSecurityTest {

    private static final Instant AHORA = Instant.parse("2026-09-02T15:00:00Z");
    private static final String CUALQUIERA = UUID.randomUUID().toString();

    private final WebApplicationContext contexto;
    private final AccessTokenIssuer emisor;
    private final ListingRepository publicaciones;
    private final Categories categorias;
    private final JdbcClient jdbc;

    private MockMvc mvc;

    FavoritesSecurityTest(
            WebApplicationContext contexto,
            AccessTokenIssuer emisor,
            ListingRepository publicaciones,
            Categories categorias,
            JdbcClient jdbc) {
        this.contexto = contexto;
        this.emisor = emisor;
        this.publicaciones = publicaciones;
        this.categorias = categorias;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void montarConLaCadenaDeSeguridad() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    // --- La lista es privada: sin token no hay nada --------------------------

    @Test
    void deberia_negar_las_cuatro_rutas_sin_token_criterio_16() throws Exception {
        mvc.perform(get("/api/v1/users/me/favorites")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users/me/favorites/" + CUALQUIERA)).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/users/me/favorites/" + CUALQUIERA)).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/users/me/favorites/" + CUALQUIERA)).andExpect(status().isUnauthorized());
    }

    /**
     * Con token y sin ningun rol, la lista responde: para tener favoritos basta con tener
     * cuenta. Se afirma el 200 y no la ausencia de 403, porque una lista vacia es una
     * respuesta legitima y lo que se prueba es que la puerta se abrio.
     */
    @Test
    void deberia_dejar_ver_su_lista_a_cualquiera_con_sesion() throws Exception {
        mvc.perform(get("/api/v1/users/me/favorites").header("Authorization", "Bearer " + tokenDe(nuevoUsuario())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").exists());
    }

    // --- RN-072: nadie marca lo suyo ----------------------------------------

    /**
     * El 403 con codigo propio, y no un 200 en silencio ni un 403 generico. Es lo que le
     * permite a la pantalla explicar por que no se guardo cuando la intencion venia
     * pendiente desde antes del ingreso (criterio 10).
     */
    @Test
    void deberia_rechazar_marcar_la_publicacion_propia_RN_072() throws Exception {
        UUID vendedor = nuevoUsuario();
        Listing suya = publicadaDe(new SellerId(vendedor));

        mvc.perform(put("/api/v1/users/me/favorites/" + suya.id())
                        .header("Authorization", "Bearer " + tokenDe(vendedor)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CATALOG_SELF_FAVORITE_FORBIDDEN"));
    }

    /** Y sobre la propia el control no se ofrece: {@code eligible} es falso (criterio 5). */
    @Test
    void no_deberia_ofrecer_el_control_sobre_la_publicacion_propia_criterio_5() throws Exception {
        UUID vendedor = nuevoUsuario();
        Listing suya = publicadaDe(new SellerId(vendedor));

        mvc.perform(get("/api/v1/users/me/favorites/" + suya.id())
                        .header("Authorization", "Bearer " + tokenDe(vendedor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorite").value(false))
                .andExpect(jsonPath("$.eligible").value(false));
    }

    // --- RN-068: lo que no se ve no se guarda --------------------------------

    /** Criterio 6. Y con 404, no con 422: decir "esto existe" ya es decir algo. */
    @Test
    void deberia_responder_404_al_marcar_lo_que_no_esta_publicado_criterio_6() throws Exception {
        Listing pausada =
                publicaciones.guardar(publicadaDe(new SellerId(nuevoUsuario())).pausar(AHORA));

        mvc.perform(put("/api/v1/users/me/favorites/" + pausada.id())
                        .header("Authorization", "Bearer " + tokenDe(nuevoUsuario())))
                .andExpect(status().isNotFound());
    }

    /** La que no existe responde exactamente igual. Es el punto de RN-068. */
    @Test
    void deberia_responder_404_al_marcar_una_publicacion_que_no_existe() throws Exception {
        mvc.perform(put("/api/v1/users/me/favorites/" + CUALQUIERA)
                        .header("Authorization", "Bearer " + tokenDe(nuevoUsuario())))
                .andExpect(status().isNotFound());
    }

    // --- El ciclo, por HTTP --------------------------------------------------

    /**
     * Marcar, ver que quedo, marcar otra vez y quitar. Criterios 2, 3 y 4 por la puerta de
     * verdad: el 204 de la segunda marca es la idempotencia vista desde fuera.
     */
    @Test
    void deberia_marcar_repetir_y_quitar_criterios_2_3_y_4() throws Exception {
        UUID quien = nuevoUsuario();
        Listing publicada = publicadaDe(new SellerId(nuevoUsuario()));
        String token = "Bearer " + tokenDe(quien);

        mvc.perform(put("/api/v1/users/me/favorites/" + publicada.id()).header("Authorization", token))
                .andExpect(status().isNoContent());
        mvc.perform(put("/api/v1/users/me/favorites/" + publicada.id()).header("Authorization", token))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/users/me/favorites/" + publicada.id()).header("Authorization", token))
                .andExpect(jsonPath("$.favorite").value(true))
                .andExpect(jsonPath("$.eligible").value(true));

        mvc.perform(get("/api/v1/users/me/favorites").header("Authorization", token))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(publicada.id().toString()));

        mvc.perform(delete("/api/v1/users/me/favorites/" + publicada.id()).header("Authorization", token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/users/me/favorites/" + publicada.id()).header("Authorization", token))
                .andExpect(jsonPath("$.favorite").value(false));
    }

    /** Quitar lo que nunca estuvo responde 204. El doble pulsado no es un error. */
    @Test
    void deberia_responder_204_al_quitar_lo_que_no_estaba() throws Exception {
        mvc.perform(delete("/api/v1/users/me/favorites/" + CUALQUIERA)
                        .header("Authorization", "Bearer " + tokenDe(nuevoUsuario())))
                .andExpect(status().isNoContent());
    }

    /**
     * RN-070 por la puerta de verdad: dos cuentas, un favorito, y la otra no lo ve. No hay
     * ruta que acepte de quien leer, asi que la unica forma de comprobarlo es pedir la
     * propia con otro token.
     */
    @Test
    void no_deberia_ensenar_los_favoritos_de_otra_persona_RN_070() throws Exception {
        UUID una = nuevoUsuario();
        UUID otra = nuevoUsuario();
        Listing publicada = publicadaDe(new SellerId(nuevoUsuario()));

        mvc.perform(put("/api/v1/users/me/favorites/" + publicada.id())
                        .header("Authorization", "Bearer " + tokenDe(una)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/users/me/favorites").header("Authorization", "Bearer " + tokenDe(otra)))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    // --- El borde ------------------------------------------------------------

    @Test
    void deberia_rechazar_un_identificador_mal_formado() throws Exception {
        mvc.perform(put("/api/v1/users/me/favorites/no-es-un-identificador")
                        .header("Authorization", "Bearer " + tokenDe(nuevoUsuario())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"));
    }

    /** Un cursor inventado es un 400, no la primera pagina otra vez. */
    @Test
    void deberia_rechazar_un_cursor_que_no_descifra() throws Exception {
        mvc.perform(get("/api/v1/users/me/favorites")
                        .param("cursor", "esto-no-es-un-cursor")
                        .header("Authorization", "Bearer " + tokenDe(nuevoUsuario())))
                .andExpect(status().isBadRequest());
    }

    /** El tope del contrato: por encima se rechaza, no se recorta en silencio. */
    @Test
    void deberia_rechazar_un_limite_por_encima_del_tope() throws Exception {
        mvc.perform(get("/api/v1/users/me/favorites")
                        .param("limit", "500")
                        .header("Authorization", "Bearer " + tokenDe(nuevoUsuario())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("limite"));
    }

    // ------------------------------------------------------------- datos

    private String tokenDe(UUID id, Role... roles) {
        User cuenta = User.rehidratar(
                UserId.nuevo(),
                new Email("alguien@sendik.co"),
                new DisplayName("Quien guarda"),
                new BirthDate(LocalDate.of(1990, 3, 4)),
                null,
                null,
                null,
                UserLocale.ES,
                UserStatus.ACTIVE,
                Instant.now(),
                Set.of(roles),
                Instant.now());

        // El sub del token tiene que ser la cuenta concreta: de el sale el BuyerId, y las
        // pruebas de RN-072 y RN-070 dependen de que sea uno y no otro.
        return emisor.emitir(conId(cuenta, id), TokenFamilyId.nueva(), Instant.now())
                .value();
    }

    private static User conId(User cuenta, UUID id) {
        return User.rehidratar(
                new UserId(id),
                cuenta.email(),
                cuenta.displayName(),
                cuenta.birthDate(),
                null,
                null,
                null,
                cuenta.locale(),
                cuenta.status(),
                cuenta.createdAt(),
                cuenta.roles(),
                cuenta.createdAt());
    }

    /** Una cuenta real: favorites.user_id y products.seller_id apuntan a users. */
    private UUID nuevoUsuario() {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO users (id, email, display_name, birth_date, status)
                        VALUES (:id, :correo, 'Alguien de prueba', DATE '1990-01-01', 'ACTIVE')
                        """).param("id", id).param("correo", id + "@ejemplo.co").update();
        return id;
    }

    private Listing publicadaDe(SellerId vendedor) {
        Listing borrador = conTomas(Listing.crearBorrador(ListingId.nuevo(), producto(vendedor), AHORA));
        Listing enRevision = publicaciones.guardar(borrador.enviarARevision(AHORA));

        return publicaciones.guardar(enRevision.aprobar(new ModeratorId(nuevoUsuario()), AHORA));
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

    private Product producto(SellerId vendedor) {
        Map<MeasurementKind, BigDecimal> valores = new EnumMap<>(MeasurementKind.class);
        MeasurementGroup.TOP.obligatorias().forEach(medida -> valores.put(medida, new BigDecimal("50.0")));

        return Product.crear(
                ProductId.nuevo(),
                vendedor,
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
