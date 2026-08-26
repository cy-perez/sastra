package co.sendik;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.sendik.identity.model.BirthDate;
import co.sendik.identity.model.DisplayName;
import co.sendik.identity.model.Email;
import co.sendik.identity.model.RawPassword;
import co.sendik.identity.model.TokenFamilyId;
import co.sendik.identity.model.User;
import co.sendik.identity.model.UserId;
import co.sendik.identity.model.UserLocale;
import co.sendik.identity.port.out.AccessTokenIssuer;
import co.sendik.identity.port.out.PasswordHasher;
import co.sendik.identity.port.out.UserRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * El camino completo de una publicacion, por HTTP y contra PostgreSQL real. HU-007.
 *
 * <p>Es la prueba que junta lo que las demas miran por separado: los controladores, la
 * cadena de seguridad, el cableado de {@code CatalogWiring}, los adaptadores JDBC, el
 * almacen de archivos y el arbol de categorias sembrado por la migracion. Cada pieza tiene
 * su prueba; esta comprueba que encajan.
 *
 * <p>Recorre los dos caminos que pide la historia: crear, subir las ocho tomas, enviar,
 * aprobar y quedar visible para cualquiera; y el mismo con rechazo, correccion y reenvio,
 * que es donde vive el {@code DELETE /rejection} sin el que un rechazo por fotos no tiene
 * salida.
 *
 * <p>Las tomas son PNG de verdad y no unos bytes con la firma correcta: el normalizador
 * decodifica y vuelve a codificar, asi que un archivo falso no pasaria de ahi.
 */
@SpringBootTest(properties = "sendik.features.publishing=true")
@ActiveProfiles("local")
@Import(PostgresTestContainer.class)
class ListingJourneyTest {

    private static final String CONTRASENA = "una-contrasena-larga-de-prueba";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final WebApplicationContext contexto;
    private final AccessTokenIssuer emisor;
    private final UserRepository usuarios;
    private final PasswordHasher hasher;
    private final JdbcClient jdbc;
    private final Clock reloj;

    private MockMvc mvc;

    ListingJourneyTest(
            WebApplicationContext contexto,
            AccessTokenIssuer emisor,
            UserRepository usuarios,
            PasswordHasher hasher,
            JdbcClient jdbc,
            Clock reloj) {
        this.contexto = contexto;
        this.emisor = emisor;
        this.usuarios = usuarios;
        this.hasher = hasher;
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    @BeforeEach
    void montarConLaCadenaDeSeguridad() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void deberia_recorrer_la_publicacion_hasta_quedar_visible_para_cualquiera() throws Exception {
        User vendedor = vendedorVerificado();
        String suToken = tokenDe(vendedor);

        String id = crearBorrador(suToken);
        subirLasOchoTomas(suToken, id);

        mvc.perform(post("/api/v1/listings/" + id + "/submission").header("Authorization", "Bearer " + suToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));

        // Antes de aprobar no la ve nadie mas, y el 404 no distingue "no existe" de
        // "no es para ti" (criterio 33).
        mvc.perform(get("/api/v1/listings/" + id)).andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/listings/" + id + "/approval")
                        .header("Authorization", "Bearer " + tokenDe(moderador())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        // Y ahora si, sin token y con la forma publica: sin version ni nada de moderacion.
        MvcResult visible = mvc.perform(get("/api/v1/listings/" + id))
                .andExpect(status().isOk())
                .andReturn();

        String cuerpo = visible.getResponse().getContentAsString();
        assertThat(cuerpo)
                .contains("Camisa de lino color hueso")
                .doesNotContain("version")
                .doesNotContain("status");

        // Y queda el rastro de quien decidio (criterio 21).
        assertThat(bitacoraDe(id)).containsExactly("APPROVED");
    }

    /**
     * El camino del rechazo, y el que descubrio que faltaba un caso de uso.
     *
     * <p>{@code REJECTED -> PENDING_REVIEW} no es una transicion valida, asi que sin
     * retomar la publicacion antes, el reenvio responde 409 y el vendedor se queda
     * atrapado. Esta prueba recorre el camino entero: rechazo, retomar, corregir y
     * reenviar (criterio 23).
     */
    @Test
    void deberia_dejar_corregir_y_reenviar_lo_rechazado() throws Exception {
        User vendedor = vendedorVerificado();
        String suToken = tokenDe(vendedor);
        String deModerador = tokenDe(moderador());

        String id = crearBorrador(suToken);
        subirLasOchoTomas(suToken, id);

        mvc.perform(post("/api/v1/listings/" + id + "/submission").header("Authorization", "Bearer " + suToken))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/listings/" + id + "/rejection")
                        .header("Authorization", "Bearer " + deModerador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"PHOTOS_UNUSABLE\",\"note\":\"Se ven movidas.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("PHOTOS_UNUSABLE"))
                .andExpect(jsonPath("$.rejectionNote").value("Se ven movidas."));

        // Reenviar sin retomar es una transicion que no existe.
        mvc.perform(post("/api/v1/listings/" + id + "/submission").header("Authorization", "Bearer " + suToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_LISTING_INVALID_STATE"));

        // Retomar la devuelve a borrador con sus datos y sus ocho tomas intactos.
        mvc.perform(delete("/api/v1/listings/" + id + "/rejection").header("Authorization", "Bearer " + suToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.images.length()").value(8));

        // Corregir una toma y reenviar.
        mvc.perform(multipart("/api/v1/listings/" + id + "/images")
                        .file(new MockMultipartFile("archivo", "toma.png", "image/png", pngDe(900, 1200)))
                        .param("position", "0")
                        .header("Authorization", "Bearer " + suToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.images.length()").value(8));

        mvc.perform(post("/api/v1/listings/" + id + "/submission").header("Authorization", "Bearer " + suToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));

        mvc.perform(post("/api/v1/listings/" + id + "/approval").header("Authorization", "Bearer " + deModerador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        assertThat(bitacoraDe(id)).containsExactly("REJECTED", "APPROVED");
    }

    /** RN-011: sin el sello no se publica, por mucho que la cuenta sea valida. */
    @Test
    void no_deberia_dejar_crear_a_quien_no_esta_verificado_RN_011() throws Exception {
        User cualquiera = cuentaNueva();

        mvc.perform(post("/api/v1/listings")
                        .header("Authorization", "Bearer " + tokenDe(cualquiera))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDeCamisa()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CATALOG_SELLER_NOT_VERIFIED"));
    }

    /** Criterio 6: enviar sin tomas dice que faltan, y no un 500. */
    @Test
    void deberia_negarse_a_enviar_un_borrador_sin_tomas_criterio_17() throws Exception {
        String suToken = tokenDe(vendedorVerificado());
        String id = crearBorrador(suToken);

        mvc.perform(post("/api/v1/listings/" + id + "/submission").header("Authorization", "Bearer " + suToken))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CATALOG_SHOTS_INCOMPLETE"));
    }

    // --- apoyo ---------------------------------------------------------------

    private String crearBorrador(String token) throws Exception {
        MvcResult creada = mvc.perform(post("/api/v1/listings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDeCamisa()))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode cuerpo = JSON.readTree(creada.getResponse().getContentAsString());
        return cuerpo.get("id").asString();
    }

    /** RN-017: las ocho de la secuencia, que es lo que el envio exige. */
    private void subirLasOchoTomas(String token, String id) throws Exception {
        for (int posicion = 0; posicion < 8; posicion++) {
            mvc.perform(multipart("/api/v1/listings/" + id + "/images")
                            .file(new MockMultipartFile("archivo", "toma.png", "image/png", pngDe(900, 1200)))
                            .param("position", String.valueOf(posicion))
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isCreated());
        }
    }

    private String cuerpoDeCamisa() {
        return "{\"categoryId\":\"" + categoriaPorSlug("camisas-y-blusas") + "\","
                + "\"title\":\"Camisa de lino color hueso\","
                + "\"description\":\"Usada dos veces, sin manchas.\","
                + "\"condition\":\"LIKE_NEW\","
                + "\"size\":{\"system\":\"ALPHA\",\"value\":\"M\"},"
                + "\"measurements\":{\"CHEST\":52.0,\"WAIST\":48.0,\"SHOULDERS\":40.0,\"SLEEVE\":58.0,\"LENGTH\":70.0},"
                + "\"color\":\"BEIGE\","
                + "\"price\":{\"amount\":185000,\"currency\":\"COP\"},"
                + "\"shipping\":{\"weightGrams\":600,\"lengthCm\":30,\"widthCm\":20,\"heightCm\":10}}";
    }

    private UUID categoriaPorSlug(String slug) {
        return jdbc.sql("SELECT id FROM categories WHERE slug = :s")
                .param("s", slug)
                .query(UUID.class)
                .single();
    }

    /**
     * El sello, escrito con la misma sentencia que lo escribiria HU-002.
     *
     * <p>Va por SQL y no por los casos de uso de verificacion para que esta prueba hable
     * de publicar y no de verificar: el camino completo de la verificacion ya lo recorre
     * {@code SellerVerificationJourneyTest}.
     */
    private User vendedorVerificado() {
        User vendedor = cuentaNueva();

        jdbc.sql("""
                        INSERT INTO seller_verifications (id, user_id, status, attempts, created_at, updated_at)
                        VALUES (gen_random_uuid(), :usuario, 'VERIFIED', 1, now(), now())
                        """).param("usuario", vendedor.id().value()).update();

        return vendedor;
    }

    private UserId moderador() {
        User quien = cuentaNueva();

        jdbc.sql("""
                        INSERT INTO user_roles (user_id, role, granted_at)
                        VALUES (:usuario, 'MODERATOR', now())
                        """).param("usuario", quien.id().value()).update();

        return quien.id();
    }

    private User cuentaNueva() {
        User nueva = User.registrar(
                UserId.nuevo(),
                new Email("ana-" + UUID.randomUUID() + "@correo.co"),
                new DisplayName("Ana Maria"),
                new BirthDate(LocalDate.of(1990, 3, 4)),
                UserLocale.ES,
                LocalDate.now(reloj),
                reloj.instant());

        usuarios.crear(nueva, hasher.hashear(new RawPassword(CONTRASENA)));
        usuarios.actualizar(nueva.conCorreoVerificado(reloj.instant()));

        return nueva;
    }

    private String tokenDe(User cuenta) {
        return emisor.emitir(cuenta, TokenFamilyId.nueva(), reloj.instant()).value();
    }

    private String tokenDe(UserId quien) {
        User conRol = usuarios.buscarPorId(quien).orElseThrow();
        return emisor.emitir(conRol, TokenFamilyId.nueva(), reloj.instant()).value();
    }

    private java.util.List<String> bitacoraDe(String publicacion) {
        return jdbc.sql("""
                        SELECT action FROM moderation_events
                        WHERE listing_id = :publicacion
                        ORDER BY created_at, action
                        """)
                .param("publicacion", UUID.fromString(publicacion))
                .query(String.class)
                .list();
    }

    private static byte[] pngDe(int ancho, int alto) {
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                imagen.setRGB(x, y, (x + y) % 2 == 0 ? 0xFFFFFF : 0x000000);
            }
        }

        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            ImageIO.write(imagen, "png", salida);
            return salida.toByteArray();
        } catch (IOException fallo) {
            throw new UncheckedIOException(fallo);
        }
    }
}
