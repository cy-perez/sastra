package co.sendik;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * Quien puede revisar una verificacion. HU-002, criterios 7 y 8.
 *
 * <p><strong>Esta es la prueba que no se puede saltar.</strong> Las de borde de
 * {@code presentation} montan el controlador sin la cadena de filtros, asi que alli la
 * autorizacion no existe y todo responde 200: comprobarla exige el contexto completo con
 * Spring Security dentro, y eso vive en {@code bootstrap}.
 *
 * <p>Lo que verifica es lo que separa un sistema con moderacion de uno donde cualquiera
 * se aprueba a si mismo:
 *
 * <ul>
 *   <li>Con token pero sin el rol, las rutas de revision responden 403.
 *   <li>Sin token, 401.
 *   <li>Con el rol, la ruta responde —lo que devuelva ya es otra cosa—.
 * </ul>
 *
 * <p>Comprueba a la vez las dos cerraduras: la regla por ruta de {@code SecurityConfig} y
 * el {@code @PreAuthorize} de cada metodo, que sin {@code @EnableMethodSecurity} habria
 * quedado decorativo sin que nada avisara.
 */
@SpringBootTest(properties = "sendik.features.seller-verification=true")
@ActiveProfiles("local")
@Import(PostgresTestContainer.class)
class VerificationReviewSecurityTest {

    private static final String CUALQUIER_ID = UserId.nuevo().toString();

    private final WebApplicationContext contexto;

    private final AccessTokenIssuer emisor;

    private MockMvc mvc;

    VerificationReviewSecurityTest(WebApplicationContext contexto, AccessTokenIssuer emisor) {
        this.contexto = contexto;
        this.emisor = emisor;
    }

    @BeforeEach
    void montarConLaCadenaDeSeguridad() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    // --- Sin el rol -----------------------------------------------------------

    @Test
    void deberia_negar_la_bandeja_a_quien_no_es_moderador() throws Exception {
        mvc.perform(get("/api/v1/verifications").with(jwt())).andExpect(status().isForbidden());
    }

    /**
     * El caso que da nombre a toda esta rebanada: si estas rutas vivieran bajo
     * {@code /api/v1/users/**}, esta peticion respondaria 200 y quien la hace se habria
     * aprobado a si mismo.
     */
    @Test
    void deberia_negar_aprobar_a_quien_no_es_moderador() throws Exception {
        mvc.perform(post("/api/v1/verifications/" + CUALQUIER_ID + "/approval").with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void deberia_negar_rechazar_a_quien_no_es_moderador() throws Exception {
        mvc.perform(post("/api/v1/verifications/" + CUALQUIER_ID + "/rejection")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"ILLEGIBLE_PHOTOS\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deberia_negar_revocar_a_quien_no_es_moderador() throws Exception {
        mvc.perform(post("/api/v1/verifications/" + CUALQUIER_ID + "/revocation")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"REQUIREMENTS_NOT_MET\"}"))
                .andExpect(status().isForbidden());
    }

    /** Ver la cedula de otra persona es lo mas sensible que hace este sistema. */
    @Test
    void deberia_negar_ver_una_imagen_a_quien_no_es_moderador() throws Exception {
        mvc.perform(get("/api/v1/verifications/" + CUALQUIER_ID + "/images/document-front")
                        .with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void deberia_negar_todo_sin_token() throws Exception {
        mvc.perform(get("/api/v1/verifications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/verifications/" + CUALQUIER_ID + "/images/selfie"))
                .andExpect(status().isUnauthorized());
    }

    // --- Con el rol -----------------------------------------------------------

    /**
     * Aqui el token se <strong>emite de verdad</strong> en lugar de usar el post-procesador
     * {@code jwt()}, y el motivo importa: ese post-procesador construye las autoridades
     * por su cuenta e ignora nuestro convertidor, asi que un token con el claim
     * {@code roles} le llegaba a la cadena sin ninguna autoridad. Con un token firmado
     * participa todo: el decodificador, el convertidor que traduce {@code roles} a
     * {@code ROLE_}, la regla de ruta y el {@code @PreAuthorize}.
     *
     * <p>Si ese convertidor se rompiera —lo advierte {@code SecurityConfig}—, toda regla
     * por rol quedaria muerta sin dar error. Esta prueba es lo que lo nota.
     */
    @Test
    void deberia_dejar_ver_la_bandeja_a_un_moderador() throws Exception {
        mvc.perform(get("/api/v1/verifications").header("Authorization", "Bearer " + tokenCon(Role.MODERATOR)))
                .andExpect(status().isOk());
    }

    /**
     * Con el rol, la peticion pasa la autorizacion y llega al caso de uso, que responde
     * 404 porque esa solicitud no existe. Un 404 aqui es exactamente lo que se busca: la
     * puerta se abrio.
     */
    @Test
    void deberia_dejar_pasar_a_un_moderador_hasta_el_caso_de_uso() throws Exception {
        mvc.perform(post("/api/v1/verifications/" + CUALQUIER_ID + "/approval")
                        .header("Authorization", "Bearer " + tokenCon(Role.MODERATOR)))
                .andExpect(status().isNotFound());
    }

    /** Un rol cualquiera no sirve: tiene que ser el de moderacion. */
    @Test
    void deberia_negar_la_bandeja_a_un_vendedor_verificado() throws Exception {
        mvc.perform(get("/api/v1/verifications").header("Authorization", "Bearer " + tokenCon(Role.SELLER, Role.BUYER)))
                .andExpect(status().isForbidden());
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
