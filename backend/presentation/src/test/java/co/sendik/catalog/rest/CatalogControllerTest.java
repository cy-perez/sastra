package co.sendik.catalog.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.sendik.catalog.dto.CatalogCursor;
import co.sendik.catalog.dto.CatalogPage;
import co.sendik.catalog.dto.ListCatalogQuery;
import co.sendik.catalog.model.ListingId;
import co.sendik.catalog.rest.mapper.CatalogCursors;
import co.sendik.catalog.usecase.ListCatalogUseCase;
import co.sendik.shared.file.FileKey;
import co.sendik.shared.port.out.PublicFileStore;
import co.sendik.shared.rest.ApiExceptionHandler;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * El borde del catalogo publico. HU-009, criterios 3 y 4.
 *
 * <p>Lo que se comprueba aqui es lo que solo se puede comprobar con HTTP delante: la forma
 * del cuerpo que fija contrato-api.md, que el cursor viaje opaco y vuelva entero, y que un
 * limite fuera de rango se rechace en vez de recortarse.
 */
class CatalogControllerTest {

    private static final Instant CUANDO = Instant.parse("2026-08-27T15:00:00Z");

    private final ListCatalogUseCase listar = mock(ListCatalogUseCase.class);
    private final PublicFileStore almacen = mock(PublicFileStore.class);

    private MockMvc mvc;

    @BeforeEach
    void montarElBorde() {
        when(almacen.direccionDe(any(FileKey.class))).thenReturn(URI.create("https://cdn.sendik.co/toma.jpg"));

        mvc = MockMvcBuilders.standaloneSetup(new CatalogController(listar, almacen))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(new LocalValidatorFactoryBean())
                .build();
    }

    /** La forma del contrato para los listados por cursor. */
    @Test
    void deberia_responder_items_con_cursor_y_si_hay_mas() throws Exception {
        when(listar.execute(any())).thenReturn(CatalogPage.ultima(List.of()));

        mvc.perform(get("/api/v1/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    /** Criterio 4: sin nada publicado la respuesta es vacia, no un error. */
    @Test
    void deberia_devolver_un_tramo_vacio_cuando_no_hay_nada_publicado_criterio_4() throws Exception {
        when(listar.execute(any())).thenReturn(CatalogPage.ultima(List.of()));

        mvc.perform(get("/api/v1/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    /** El cursor sale codificado y vuelve a entrar entero. */
    @Test
    void deberia_devolver_un_cursor_que_al_mandarlo_de_vuelta_dice_lo_mismo() throws Exception {
        ListingId ultima = new ListingId(UUID.randomUUID());
        CatalogCursor siguiente = new CatalogCursor(CUANDO, ultima);

        when(listar.execute(any())).thenReturn(new CatalogPage(List.of(), siguiente, true));

        String texto = CatalogCursors.texto(siguiente);

        mvc.perform(get("/api/v1/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.nextCursor").value(texto));

        mvc.perform(get("/api/v1/listings").param("cursor", texto)).andExpect(status().isOk());

        ArgumentCaptor<ListCatalogQuery> consulta = ArgumentCaptor.forClass(ListCatalogQuery.class);
        verify(listar, org.mockito.Mockito.atLeastOnce()).execute(consulta.capture());

        assertQueLlevaElCursor(consulta.getAllValues().getLast(), siguiente);
    }

    /**
     * Criterio 3: un limite por encima del tope se rechaza con 400.
     *
     * <p>No se recorta en silencio: quien pide 500 y recibe 50 sin que nadie se lo diga
     * cree que ya tiene el catalogo entero.
     *
     * <p><strong>Lo protegen dos guardas y esta prueba ejercita la de dentro.</strong> En
     * la aplicacion en marcha salta primero el {@code @Max} del parametro; en un
     * {@code standaloneSetup} no hay procesador de validacion de metodos, asi que la
     * peticion llega al controlador y la rechaza {@code ListCatalogQuery}. Que las dos
     * respondan lo mismo es justo lo que hace que la de dentro no sea redundante: protege
     * a cualquiera que use el caso de uso, tambien desde otro borde.
     */
    @Test
    void deberia_rechazar_un_limite_por_encima_del_tope_criterio_3() throws Exception {
        mvc.perform(get("/api/v1/listings").param("limit", "500")).andExpect(status().isBadRequest());
    }

    /** Criterio 4 del cursor: uno inventado es un 400 y no el primer tramo. */
    @Test
    void deberia_rechazar_un_cursor_que_no_descifra() throws Exception {
        mvc.perform(get("/api/v1/listings").param("cursor", "esto-no-es-un-cursor"))
                .andExpect(status().isBadRequest());
    }

    private static void assertQueLlevaElCursor(ListCatalogQuery consulta, CatalogCursor esperado) {
        org.assertj.core.api.Assertions.assertThat(consulta.desde()).isEqualTo(esperado);
    }
}
