package co.sendik;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * Con {@code FEATURE_PUBLISHING} apagada, la publicacion no existe. HU-007, criterio 3.
 *
 * <p><strong>404 y no 403, y la diferencia es el sentido de la bandera.</strong> Un 403
 * diria que la funcionalidad esta ahi y que a quien pregunta le falta permiso; un 404 dice
 * que no hay nada, que es la verdad mientras la bandera este apagada. Lo consigue el
 * {@code @ConditionalOnProperty} de los controladores: no se crean, asi que no hay ruta.
 *
 * <p>Es una clase aparte y no un metodo mas de {@link ListingSecurityTest} porque la
 * bandera es una propiedad del contexto: encenderla y apagarla dentro de la misma clase no
 * se puede sin levantar dos contextos igualmente.
 *
 * <p>Las peticiones van <strong>con token</strong>. Sin el, casi todas responderian 401
 * antes de llegar a saber si la ruta existe, y lo que se prueba aqui es lo segundo.
 * {@code ApplicationContextTest} ya comprueba que la bandera nace apagada.
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(PostgresTestContainer.class)
class PublishingDisabledTest {

    private static final String CUALQUIERA = UUID.randomUUID().toString();

    private final WebApplicationContext contexto;

    private MockMvc mvc;

    PublishingDisabledTest(WebApplicationContext contexto) {
        this.contexto = contexto;
    }

    @BeforeEach
    void montarConLaCadenaDeSeguridad() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void no_deberia_existir_ninguna_ruta_del_vendedor_criterio_3() throws Exception {
        mvc.perform(post("/api/v1/listings")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":\"" + CUALQUIERA + "\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/listings/" + CUALQUIERA).with(jwt())).andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/listings/" + CUALQUIERA)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":\"" + CUALQUIERA + "\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/listings/" + CUALQUIERA + "/price")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":{\"amount\":1,\"currency\":\"COP\"}}"))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/listings/" + CUALQUIERA + "/shipping")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weightGrams\":1,\"lengthCm\":1,\"widthCm\":1,\"heightCm\":1}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/listings/" + CUALQUIERA + "/images").with(jwt()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/listings/" + CUALQUIERA + "/images/" + CUALQUIERA)
                        .with(jwt()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/listings/" + CUALQUIERA + "/submission").with(jwt()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/listings/" + CUALQUIERA + "/submission").with(jwt()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/listings/" + CUALQUIERA + "/rejection").with(jwt()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/listings/" + CUALQUIERA + "/pause").with(jwt()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/listings/" + CUALQUIERA + "/pause").with(jwt()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/listings/" + CUALQUIERA + "/archival").with(jwt()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/users/me/listings").with(jwt())).andExpect(status().isNotFound());
    }

    /**
     * Las tres del moderador tambien.
     *
     * <p>Van con un token sin rol a proposito: con la bandera apagada la respuesta tiene
     * que ser 404 y no el 403 que daria la regla de rol. Si saliera 403, la funcionalidad
     * estaria anunciada aunque no se pueda usar.
     */
    @Test
    void no_deberia_existir_ninguna_ruta_del_moderador_criterio_3() throws Exception {
        mvc.perform(post("/api/v1/listings/" + CUALQUIERA + "/approval").with(jwt()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/listings/" + CUALQUIERA + "/rejection")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"PHOTOS_UNUSABLE\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/listings/" + CUALQUIERA + "/removal")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"PROHIBITED_ITEM\"}"))
                .andExpect(status().isNotFound());
    }
}
