package co.sendik.catalog.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.sendik.catalog.dto.ListSellerListingsQuery;
import co.sendik.catalog.model.SellerId;
import co.sendik.catalog.usecase.ListSellerListingsUseCase;
import co.sendik.shared.file.FileKey;
import co.sendik.shared.port.out.PublicFileStore;
import co.sendik.shared.rest.ApiExceptionHandler;
import java.net.URI;
import java.util.List;
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

/** El listado propio del vendedor. HU-007, ultimo endpoint de la tabla. */
class SellerListingsControllerTest {

    private static final SellerId VENDEDOR = new SellerId(UUID.randomUUID());

    private final ListSellerListingsUseCase listar = mock(ListSellerListingsUseCase.class);
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
                    .subject(VENDEDOR.value().toString())
                    .build();
        }
    }

    @BeforeEach
    void montarElBorde() {
        when(almacen.direccionDe(any(FileKey.class))).thenReturn(URI.create("https://cdn.sendik.co/x.jpg"));

        mvc = MockMvcBuilders.standaloneSetup(new SellerListingsController(listar, almacen))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(new TokenDePrueba())
                .build();
    }

    @Test
    void deberia_devolver_las_del_vendedor_del_token() throws Exception {
        when(listar.execute(any())).thenReturn(List.of(CatalogoDelBorde.borrador(VENDEDOR)));

        mvc.perform(get("/api/v1/users/me/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));

        ArgumentCaptor<ListSellerListingsQuery> consulta = ArgumentCaptor.forClass(ListSellerListingsQuery.class);
        verify(listar).execute(consulta.capture());
        assertThat(consulta.getValue().vendedor()).isEqualTo(VENDEDOR);
    }

    @Test
    void deberia_respetar_la_pagina_y_el_tamano_pedidos() throws Exception {
        when(listar.execute(any())).thenReturn(List.of());

        mvc.perform(get("/api/v1/users/me/listings").param("page", "2").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());

        ArgumentCaptor<ListSellerListingsQuery> consulta = ArgumentCaptor.forClass(ListSellerListingsQuery.class);
        verify(listar).execute(consulta.capture());
        assertThat(consulta.getValue().pagina()).isEqualTo(2);
        assertThat(consulta.getValue().tamano()).isEqualTo(5);
    }

    /**
     * El tope lo pone la consulta y no el controlador, y aqui se comprueba que ese tope
     * llega hasta el borde en vez de quedarse en un 500.
     */
    @Test
    void deberia_rechazar_un_tamano_de_pagina_absurdo() throws Exception {
        mvc.perform(get("/api/v1/users/me/listings").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"));
    }
}
