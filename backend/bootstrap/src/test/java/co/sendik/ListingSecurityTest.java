package co.sendik;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Quien puede hacer que con una publicacion. HU-007, criterios 25 y 33.
 *
 * <p><strong>Es la prueba que las de {@code presentation} no pueden hacer.</strong> Alli
 * el montaje es autonomo y no tiene cadena de filtros, asi que la autorizacion no existe y
 * todo responde 200. Comprobarla exige el contexto completo con Spring Security dentro.
 *
 * <p>Lo que se verifica aqui es lo que separa un catalogo con moderacion de uno donde
 * cualquiera publica lo que quiera:
 *
 * <ul>
 *   <li>Las tres rutas de decision exigen rol de moderador, aunque compartan ruta base con
 *       las del vendedor. Es lo que hace que la separacion por metodo y patron de
 *       {@code SecurityConfig} sea de verdad y no una intencion.
 *   <li>Escribir exige token.
 *   <li>Leer una publicacion no lo exige, y ese es el punto: la ruta es publica y lo que
 *       decide que se ve es el caso de uso. Un 401 aqui delataria que existe.
 * </ul>
 */
@SpringBootTest(properties = "sendik.features.publishing=true")
@ActiveProfiles("local")
@Import(PostgresTestContainer.class)
class ListingSecurityTest {

    private static final String CUALQUIERA = UUID.randomUUID().toString();

    private final WebApplicationContext contexto;

    private final AccessTokenIssuer emisor;

    private MockMvc mvc;

    ListingSecurityTest(WebApplicationContext contexto, AccessTokenIssuer emisor) {
        this.contexto = contexto;
        this.emisor = emisor;
    }

    @BeforeEach
    void montarConLaCadenaDeSeguridad() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    // --- Criterio 25: decidir es del moderador -------------------------------

    /**
     * El caso que da nombre a esta rebanada. Estas rutas viven bajo
     * {@code /api/v1/listings/{id}}, que es donde tambien escribe el vendedor: sin la
     * regla por metodo y patron, esta peticion responderia 404 en vez de 403 y quien la
     * hace habria aprobado su propia publicacion.
     */
    @Test
    void deberia_negar_aprobar_a_quien_no_es_moderador_criterio_25() throws Exception {
        mvc.perform(post("/api/v1/listings/" + CUALQUIERA + "/approval").with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void deberia_negar_rechazar_a_quien_no_es_moderador_criterio_25() throws Exception {
        mvc.perform(post("/api/v1/listings/" + CUALQUIERA + "/rejection")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"PHOTOS_UNUSABLE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deberia_negar_retirar_a_quien_no_es_moderador_criterio_31() throws Exception {
        mvc.perform(post("/api/v1/listings/" + CUALQUIERA + "/removal")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"PROHIBITED_ITEM\"}"))
                .andExpect(status().isForbidden());
    }

    /** Un rol cualquiera no sirve: tiene que ser el de moderacion. */
    @Test
    void deberia_negar_aprobar_a_un_vendedor_verificado() throws Exception {
        mvc.perform(post("/api/v1/listings/" + CUALQUIERA + "/approval")
                        .header("Authorization", "Bearer " + tokenCon(Role.SELLER, Role.BUYER)))
                .andExpect(status().isForbidden());
    }

    /**
     * Con el rol, la peticion pasa la autorizacion y llega al caso de uso, que responde
     * 404 porque esa publicacion no existe. Un 404 aqui es lo que se busca: la puerta se
     * abrio.
     */
    @Test
    void deberia_dejar_pasar_a_un_moderador_hasta_el_caso_de_uso() throws Exception {
        mvc.perform(post("/api/v1/listings/" + CUALQUIERA + "/approval")
                        .header("Authorization", "Bearer " + tokenCon(Role.MODERATOR)))
                .andExpect(status().isNotFound());
    }

    // --- Escribir exige token ------------------------------------------------

    @Test
    void deberia_negar_sin_token_todo_lo_que_escribe() throws Exception {
        mvc.perform(post("/api/v1/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/listings/" + CUALQUIERA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/listings/" + CUALQUIERA + "/submission")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/listings/" + CUALQUIERA + "/submission")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/listings/" + CUALQUIERA + "/rejection")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/listings/" + CUALQUIERA + "/pause")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/listings/" + CUALQUIERA + "/pause")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/listings/" + CUALQUIERA + "/archival")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/listings/" + CUALQUIERA + "/images")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users/me/listings")).andExpect(status().isUnauthorized());
    }

    /**
     * Con token y sin rol, las rutas del vendedor pasan la cadena y llegan al caso de uso.
     * Es la otra mitad de la prueba anterior: la regla del moderador no puede haberse
     * llevado por delante las del dueno.
     */
    @Test
    void deberia_dejar_pasar_al_vendedor_hasta_el_caso_de_uso() throws Exception {
        mvc.perform(post("/api/v1/listings/" + CUALQUIERA + "/pause").with(conSesion()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/listings/" + CUALQUIERA + "/archival").with(conSesion()))
                .andExpect(status().isNotFound());
    }

    /**
     * Un token con sesion valida y sin ningun rol.
     *
     * <p>El sujeto tiene que ser un UUID: el borde lo convierte en {@code SellerId} y
     * cualquier otra cosa sale como 400, que taparia lo que esta prueba mira. El
     * post-procesador {@code jwt()} pone "user" por omision.
     */
    private static org.springframework.test.web.servlet.request.RequestPostProcessor conSesion() {
        return jwt().jwt(token -> token.subject(UUID.randomUUID().toString()));
    }

    // --- Criterio 33: leer es publico ---------------------------------------

    /**
     * Sin token, 404 y no 401. La diferencia es todo el criterio 33: con 401, quien
     * pregunta sabria que la publicacion existe pero no es suya.
     */
    @Test
    void deberia_responder_404_y_no_401_al_leer_sin_token_criterio_33() throws Exception {
        mvc.perform(get("/api/v1/listings/" + CUALQUIERA)).andExpect(status().isNotFound());
    }

    /** Un token real con los roles que se le digan, firmado con la clave del entorno. */
    private String tokenCon(Role... roles) {
        User cuenta = User.rehidratar(
                UserId.nuevo(),
                new Email("moderador@sendik.co"),
                new DisplayName("Quien revisa"),
                new BirthDate(LocalDate.of(1990, 3, 4)),
                null,
                null,
                null,
                UserLocale.ES,
                UserStatus.ACTIVE,
                Instant.now(),
                java.util.Set.of(roles),
                Instant.now());

        return emisor.emitir(cuenta, TokenFamilyId.nueva(), Instant.now()).value();
    }
}
