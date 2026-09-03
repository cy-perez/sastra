package co.sendik;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * El tope del listado por cursor, con el contexto entero. HU-009.
 *
 * <p><strong>Esta clase existe porque la prueba equivalente de {@code presentation} decia
 * la verdad y aun asi no protegia.</strong> {@code CatalogControllerTest} afirma que
 * {@code ?limit=500} responde 400 y pasa desde HU-009, pero su montaje autonomo de MockMvc
 * no crea el proxy de {@code @Validated}: alli la restriccion la aplica otro camino y la
 * excepcion que rompia la respuesta de verdad nunca se lanza.
 *
 * <p>Con el contexto completo, la que salta es {@code ConstraintViolationException} desde
 * el interceptor de validacion de metodos, y hasta HU-011 no tenia manejador: caia en el de
 * {@code Exception} y respondia 500, con la traza entera en nivel error, para cualquiera
 * que escribiera un limite grande en la barra de direcciones.
 *
 * <p>contrato-api.md es explicito sobre lo que debe pasar: «El tamano va acotado a 50 y por
 * encima se rechaza con 400, no se recorta en silencio». Esta es la prueba que lo sostiene
 * donde la aplicacion de verdad vive.
 */
@SpringBootTest(properties = "sendik.features.catalog=true")
@ActiveProfiles("local")
@Import(PostgresTestContainer.class)
class CatalogLimitTest {

    private final WebApplicationContext contexto;

    private MockMvc mvc;

    CatalogLimitTest(WebApplicationContext contexto) {
        this.contexto = contexto;
    }

    @BeforeEach
    void montarConLaCadenaDeSeguridad() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    /**
     * El detalle por campo, ademas del estado: quien manda mal dos parametros sabe cual.
     *
     * <p><strong>Y el nombre es el del contrato, no el del argumento Java.</strong>
     * contrato-api.md dice que {@code field} es el campo que el cliente escribio. Salia
     * {@code "limite"} —el identificador interno del servidor, en espanol— porque el
     * argumento se llamaba distinto que su {@code @RequestParam}. Esta asercion es lo que
     * lo fija.
     */
    @Test
    void deberia_rechazar_con_400_un_limite_por_encima_del_tope() throws Exception {
        mvc.perform(get("/api/v1/listings").param("limit", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("limit"))
                .andExpect(jsonPath("$.errors[0].code").value("VALIDATION_MAX"));
    }

    /** Y por debajo del minimo, lo mismo: cero elementos no es una pagina. */
    @Test
    void deberia_rechazar_con_400_un_limite_de_cero() throws Exception {
        mvc.perform(get("/api/v1/listings").param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("VALIDATION_MIN"));
    }

    /** El escaparate de un vendedor comparte el tope y compartia el defecto. */
    @Test
    void deberia_rechazar_con_400_un_limite_grande_en_el_escaparate_del_vendedor() throws Exception {
        mvc.perform(get("/api/v1/sellers/" + java.util.UUID.randomUUID() + "/listings")
                        .param("limit", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"));
    }
}
