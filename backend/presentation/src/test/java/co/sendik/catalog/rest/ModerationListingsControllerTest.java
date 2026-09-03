package co.sendik.catalog.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.sendik.catalog.dto.ListPendingListingsQuery;
import co.sendik.catalog.dto.PendingListingsResult;
import co.sendik.catalog.model.AttentionReason;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.model.ListingStatus;
import co.sendik.catalog.model.SellerId;
import co.sendik.catalog.usecase.ListPendingListingsUseCase;
import co.sendik.shared.file.FileKey;
import co.sendik.shared.port.out.PublicFileStore;
import co.sendik.shared.rest.ApiExceptionHandler;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.MethodParameter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** La bandeja del moderador de publicaciones. HU-008. */
class ModerationListingsControllerTest {

    /** Quien mira la bandeja. El rol lo comprueba la cadena, no este borde. */
    private static final SellerId MODERADOR = new SellerId(UUID.randomUUID());

    private static final SellerId OTRO = new SellerId(UUID.randomUUID());

    private final ListPendingListingsUseCase listar = mock(ListPendingListingsUseCase.class);
    private final PublicFileStore almacen = mock(PublicFileStore.class);

    private MockMvc mvc;

    private static final class TokenDePrueba implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parametro) {
            return Jwt.class.equals(parametro.getParameterType());
        }

        @Override
        public Object resolveArgument(
                MethodParameter parametro,
                ModelAndViewContainer contenedor,
                NativeWebRequest peticion,
                WebDataBinderFactory fabrica) {
            return Jwt.withTokenValue("da-igual")
                    .header("alg", "HS256")
                    .subject(MODERADOR.value().toString())
                    .build();
        }
    }

    @BeforeEach
    void montarElBorde() {
        when(almacen.direccionDe(any(FileKey.class))).thenReturn(URI.create("https://cdn.sendik.co/frontal.jpg"));

        mvc = MockMvcBuilders.standaloneSetup(new ModerationListingsController(listar, almacen))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(new TokenDePrueba())
                .build();
    }

    /** La cola devuelve una pagina; lo que se prueba aqui no es el «hay mas». */
    private static PendingListingsResult unaPagina(List<Listing> cola) {
        return new PendingListingsResult(cola, false);
    }

    @Test
    void deberia_devolver_una_fila_por_publicacion_que_espera() throws Exception {
        when(listar.execute(any()))
                .thenReturn(unaPagina(
                        List.of(CatalogoDelBorde.con(OTRO, ListingStatus.PENDING_REVIEW, null, null, Set.of()))));

        mvc.perform(get("/api/v1/moderation/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("Camisa de lino color hueso"))
                .andExpect(jsonPath("$.items[0].price.currency").value("COP"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    /**
     * Que «hay mas» lo diga el servidor, y no la longitud de la pagina.
     *
     * <p>Deducirlo de que {@code items} venga lleno se equivoca justo cuando el total es
     * multiplo exacto del tamano: la ultima pagina viene llena y la pantalla ofrece un
     * «Siguiente» hacia una pagina vacia. Las dos pruebas sujetan los dos lados, porque
     * ese caso es indistinguible del otro sin este campo.
     */
    @Test
    void deberia_decir_que_hay_mas_cuando_detras_queda_algo() throws Exception {
        when(listar.execute(any()))
                .thenReturn(new PendingListingsResult(
                        List.of(CatalogoDelBorde.con(OTRO, ListingStatus.PENDING_REVIEW, null, null, Set.of())), true));

        mvc.perform(get("/api/v1/moderation/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasMore").value(true));
    }

    @Test
    void deberia_decir_que_no_hay_mas_aunque_la_pagina_venga_llena() throws Exception {
        when(listar.execute(any()))
                .thenReturn(new PendingListingsResult(
                        List.of(CatalogoDelBorde.con(OTRO, ListingStatus.PENDING_REVIEW, null, null, Set.of())),
                        false));

        mvc.perform(get("/api/v1/moderation/listings").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    /**
     * {@code waitingSince} sale de {@code submitted_at} y no de {@code updated_at}.
     *
     * <p>Se afirma el valor exacto y no solo que exista: el ayudante sella los dos campos
     * en instantes distintos justamente para que esta prueba pueda distinguirlos. Con
     * {@code .exists()} y los dos iguales, cambiar el mapeador a {@code updatedAt} no
     * rompia nada, y esa linea es la que hace visible en pantalla el motivo de V12.
     */
    @Test
    void deberia_decir_cuando_entro_a_revision_y_no_cuando_se_toco_HU_008() throws Exception {
        when(listar.execute(any()))
                .thenReturn(unaPagina(
                        List.of(CatalogoDelBorde.con(OTRO, ListingStatus.PENDING_REVIEW, null, null, Set.of()))));

        mvc.perform(get("/api/v1/moderation/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].waitingSince").value(CatalogoDelBorde.ENTRO_A_REVISION.toString()));
    }

    /** La toma frontal, que es lo que deja reconocer la publicacion sin abrirla. */
    @Test
    void deberia_devolver_la_toma_frontal_como_portada() throws Exception {
        when(listar.execute(any()))
                .thenReturn(unaPagina(
                        List.of(CatalogoDelBorde.con(OTRO, ListingStatus.PENDING_REVIEW, null, null, Set.of()))));

        mvc.perform(get("/api/v1/moderation/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].coverUrl").value("https://cdn.sendik.co/frontal.jpg"));
    }

    /**
     * Criterio 12 y RN-063: la pantalla tiene que poder avisar antes de que se pulse.
     *
     * <p>Y solo eso: el identificador del vendedor no sale, para que la bandeja no sea de
     * paso una lista de quien vende que.
     */
    @Test
    void deberia_decir_si_la_publicacion_es_de_quien_mira_RN_063() throws Exception {
        when(listar.execute(any()))
                .thenReturn(unaPagina(List.of(
                        CatalogoDelBorde.con(MODERADOR, ListingStatus.PENDING_REVIEW, null, null, Set.of()),
                        CatalogoDelBorde.con(OTRO, ListingStatus.PENDING_REVIEW, null, null, Set.of()))));

        mvc.perform(get("/api/v1/moderation/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].own").value(true))
                .andExpect(jsonPath("$.items[1].own").value(false))
                .andExpect(jsonPath("$.items[0].sellerId").doesNotExist());
    }

    /** Criterio 6: el moderador si ve por que, al contrario que el vendedor. */
    @Test
    void deberia_decir_por_que_una_publicacion_pide_mirarse_con_mas_cuidado_RN_020() throws Exception {
        when(listar.execute(any()))
                .thenReturn(unaPagina(List.of(CatalogoDelBorde.con(
                        OTRO,
                        ListingStatus.PENDING_REVIEW,
                        null,
                        null,
                        Set.of(AttentionReason.PRICE_OUT_OF_RANGE, AttentionReason.GALLERY_UPLOAD)))));

        mvc.perform(get("/api/v1/moderation/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].requiresAttention").value(true))
                .andExpect(jsonPath("$.items[0].attentionReasons").isArray())
                .andExpect(jsonPath("$.items[0].attentionReasons.length()").value(2));
    }

    @Test
    void deberia_responder_una_bandeja_vacia_sin_inventar_filas_criterio_4() throws Exception {
        when(listar.execute(any())).thenReturn(unaPagina(List.of()));

        mvc.perform(get("/api/v1/moderation/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void deberia_respetar_la_pagina_y_el_tamano_pedidos() throws Exception {
        when(listar.execute(any())).thenReturn(unaPagina(List.of()));

        mvc.perform(get("/api/v1/moderation/listings").param("page", "3").param("size", "10"))
                .andExpect(status().isOk());

        ArgumentCaptor<ListPendingListingsQuery> consulta = ArgumentCaptor.forClass(ListPendingListingsQuery.class);
        verify(listar).execute(consulta.capture());
        assertThat(consulta.getValue().pagina()).isEqualTo(3);
        assertThat(consulta.getValue().tamano()).isEqualTo(10);
    }

    /** El tope lo pone la consulta; aqui se comprueba que llega al borde y no como 500. */
    @Test
    void deberia_rechazar_un_tamano_de_pagina_absurdo() throws Exception {
        mvc.perform(get("/api/v1/moderation/listings").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"));
    }
}
