package co.sendik.identity.rest;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.sendik.identity.port.out.FinancialInstitutions.FinancialInstitution;
import co.sendik.identity.usecase.ListFinancialInstitutionsUseCase;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** El catalogo que ofrece el formulario de la cuenta bancaria. Criterio 4 de HU-002. */
class FinancialInstitutionsControllerTest {

    private final ListFinancialInstitutionsUseCase caso = mock(ListFinancialInstitutionsUseCase.class);

    private MockMvc mvc;

    @BeforeEach
    void montarElBorde() {
        mvc = MockMvcBuilders.standaloneSetup(new FinancialInstitutionsController(caso))
                .build();
    }

    @Test
    void deberia_devolver_las_entidades_activas() throws Exception {
        when(caso.execute())
                .thenReturn(List.of(
                        new FinancialInstitution("bancolombia", "Bancolombia", false),
                        new FinancialInstitution("nequi", "Nequi", true)));

        mvc.perform(get("/api/v1/financial-institutions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("bancolombia"))
                .andExpect(jsonPath("$[0].name").value("Bancolombia"))
                // La pantalla lo necesita para saber que tipos de cuenta ofrecer: una
                // billetera no tiene cuenta de ahorros.
                .andExpect(jsonPath("$[0].wallet").value(false))
                .andExpect(jsonPath("$[1].wallet").value(true));
    }

    @Test
    void deberia_responder_una_lista_vacia_sin_reventar() throws Exception {
        when(caso.execute()).thenReturn(List.of());

        mvc.perform(get("/api/v1/financial-institutions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
