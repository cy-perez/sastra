package co.sendik.catalog.rest;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.sendik.catalog.dto.CategoryView;
import co.sendik.catalog.model.CategoryId;
import co.sendik.catalog.model.MeasurementGroup;
import co.sendik.catalog.model.SizeSystem;
import co.sendik.catalog.usecase.ListCategoriesUseCase;
import co.sendik.shared.rest.ApiExceptionHandler;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * El arbol de categorias tal como lo recibe el formulario de publicar.
 *
 * <p>Lo que importa aqui es que salga <strong>armado y completo</strong>: sin las medidas
 * obligatorias, el formulario no sabe que campos pintar; sin {@code allowsUsed}, ofrece
 * las cuatro condiciones en una categoria de tecnologia y el envio falla despues con un
 * 422 que la persona no vio venir (RN-064).
 */
class CategoriesControllerTest {

    private final ListCategoriesUseCase listar = mock(ListCategoriesUseCase.class);

    private MockMvc mvc;

    @BeforeEach
    void montarElBorde() {
        mvc = MockMvcBuilders.standaloneSetup(new CategoriesController(listar))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void deberia_devolver_las_familias_con_sus_categorias_dentro() throws Exception {
        when(listar.execute()).thenReturn(List.of(familiaDeModa(), familiaDeTecnologia()));

        mvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].slug").value("tops"))
                .andExpect(jsonPath("$[0].familySlug").doesNotExist())
                .andExpect(jsonPath("$[0].children[0].slug").value("camisas-y-blusas"))
                .andExpect(jsonPath("$[0].children[0].familySlug").value("tops"));
    }

    /** Los dos idiomas viajan juntos: el cliente elige sin volver a preguntar. */
    @Test
    void deberia_traer_el_nombre_en_los_dos_idiomas() throws Exception {
        when(listar.execute()).thenReturn(List.of(familiaDeModa()));

        mvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].children[0].nameEs").value("Camisas y blusas"))
                .andExpect(jsonPath("$[0].children[0].nameEn").value("Shirts and blouses"));
    }

    /**
     * Las medidas salen calculadas del grupo. Si no salieran, el frontend tendria que
     * repetir la tabla de grupos de medida, que es una regla del dominio.
     */
    @Test
    void deberia_decir_que_medidas_pide_cada_categoria() throws Exception {
        when(listar.execute()).thenReturn(List.of(familiaDeModa()));

        // El conjunto entero y no una muestra: el motivo de que este endpoint mande las
        // medidas calculadas es que el formulario no tenga que repetir la tabla de grupos,
        // y con media lista tendria que adivinar el resto.
        mvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].children[0].requiredMeasurements", hasSize(4)))
                .andExpect(jsonPath(
                        "$[0].children[0].requiredMeasurements",
                        containsInAnyOrder("CHEST", "LENGTH", "SHOULDERS", "SLEEVE")))
                .andExpect(jsonPath("$[0].children[0].sizeSystems", hasSize(2)))
                .andExpect(jsonPath("$[0].children[0].sizeSystems", containsInAnyOrder("ALPHA", "NUMERIC_CO")));
    }

    /** RN-064: la tecnologia no admite lo usado, y el formulario tiene que saberlo antes. */
    @Test
    void deberia_decir_cual_categoria_no_admite_lo_usado_RN_064() throws Exception {
        when(listar.execute()).thenReturn(List.of(familiaDeModa(), familiaDeTecnologia()));

        mvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].children[0].allowsUsed").value(true))
                .andExpect(jsonPath("$[1].children[0].allowsUsed").value(false));
    }

    /** Un arbol vacio es una respuesta valida y no un error: sale una lista vacia. */
    @Test
    void deberia_responder_una_lista_vacia_sin_categorias() throws Exception {
        when(listar.execute()).thenReturn(List.of());

        mvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private static CategoryView familiaDeModa() {
        CategoryView camisas = new CategoryView(
                CategoryId.nuevo(),
                "camisas-y-blusas",
                "Camisas y blusas",
                "Shirts and blouses",
                "tops",
                Set.of(SizeSystem.ALPHA, SizeSystem.NUMERIC_CO),
                MeasurementGroup.TOP.obligatorias(),
                true,
                List.of());

        return new CategoryView(
                CategoryId.nuevo(), "tops", "Parte superior", "Tops", null, Set.of(), Set.of(), true, List.of(camisas));
    }

    private static CategoryView familiaDeTecnologia() {
        CategoryView celulares = new CategoryView(
                CategoryId.nuevo(),
                "celulares-y-tabletas",
                "Celulares y tabletas",
                "Phones and tablets",
                "tech",
                Set.of(SizeSystem.ONE_SIZE),
                MeasurementGroup.DEVICE.obligatorias(),
                false,
                List.of());

        return new CategoryView(
                CategoryId.nuevo(), "tech", "Tecnología", "Tech", null, Set.of(), Set.of(), false, List.of(celulares));
    }
}
