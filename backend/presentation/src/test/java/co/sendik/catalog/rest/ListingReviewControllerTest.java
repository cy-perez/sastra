package co.sendik.catalog.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.sendik.catalog.dto.ApproveListingCommand;
import co.sendik.catalog.dto.RejectListingCommand;
import co.sendik.catalog.dto.TakeDownListingCommand;
import co.sendik.catalog.exception.InvalidListingTransitionException;
import co.sendik.catalog.exception.SelfModerationForbiddenException;
import co.sendik.catalog.model.ListingRejectionReason;
import co.sendik.catalog.model.ListingStatus;
import co.sendik.catalog.model.ModeratorId;
import co.sendik.catalog.model.SellerId;
import co.sendik.catalog.usecase.ApproveListingUseCase;
import co.sendik.catalog.usecase.RejectListingUseCase;
import co.sendik.catalog.usecase.TakeDownListingUseCase;
import co.sendik.shared.file.FileKey;
import co.sendik.shared.port.out.PublicFileStore;
import co.sendik.shared.rest.ApiExceptionHandler;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * El borde de la decision del moderador. HU-007, criterios 21, 22, 24 y 31.
 *
 * <p>Que estas rutas exijan rol no se prueba aqui: el montaje autonomo no tiene cadena de
 * filtros ni {@code @PreAuthorize}, asi que una prueba de 403 por falta de rol seria falsa
 * en los dos sentidos. Vive en {@code bootstrap}, con el contexto de verdad. Aqui se
 * prueba lo que si es de este archivo: que cada ruta llama a su caso de uso con lo que
 * llego, y que las excepciones salen con su estado.
 */
class ListingReviewControllerTest {

    private static final ModeratorId MODERADOR = new ModeratorId(UUID.randomUUID());

    private final ApproveListingUseCase aprobar = mock(ApproveListingUseCase.class);
    private final RejectListingUseCase rechazar = mock(RejectListingUseCase.class);
    private final TakeDownListingUseCase retirar = mock(TakeDownListingUseCase.class);
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
        when(almacen.direccionDe(any(FileKey.class))).thenReturn(URI.create("https://cdn.sendik.co/x.jpg"));

        mvc = MockMvcBuilders.standaloneSetup(new ListingReviewController(aprobar, rechazar, retirar, almacen))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(new TokenDePrueba())
                .build();
    }

    @Test
    void deberia_aprobar_y_devolver_la_publicacion_criterio_21() throws Exception {
        SellerId vendedor = new SellerId(UUID.randomUUID());
        String id = UUID.randomUUID().toString();
        when(aprobar.execute(any())).thenReturn(CatalogoDelBorde.publicada(vendedor));

        mvc.perform(post("/api/v1/listings/" + id + "/approval"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        ArgumentCaptor<ApproveListingCommand> comando = ArgumentCaptor.forClass(ApproveListingCommand.class);
        verify(aprobar).execute(comando.capture());
        assertThat(comando.getValue().moderador()).isEqualTo(MODERADOR);
    }

    /** El moderador sale del token. Con uno del cuerpo, cualquiera firmaria con otro nombre. */
    @Test
    void deberia_tomar_el_moderador_del_token() throws Exception {
        when(rechazar.execute(any())).thenReturn(rechazada());

        mvc.perform(post("/api/v1/listings/" + UUID.randomUUID() + "/rejection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"PHOTOS_UNUSABLE\",\"note\":\"Se ven borrosas.\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<RejectListingCommand> comando = ArgumentCaptor.forClass(RejectListingCommand.class);
        verify(rechazar).execute(comando.capture());
        assertThat(comando.getValue().moderador()).isEqualTo(MODERADOR);
        assertThat(comando.getValue().motivo()).isEqualTo(ListingRejectionReason.PHOTOS_UNUSABLE);
        assertThat(comando.getValue().nota()).isEqualTo("Se ven borrosas.");
    }

    @Test
    void deberia_devolver_el_motivo_y_la_nota_al_rechazar_criterio_22() throws Exception {
        when(rechazar.execute(any())).thenReturn(rechazada());

        mvc.perform(post("/api/v1/listings/" + UUID.randomUUID() + "/rejection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"PHOTOS_UNUSABLE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("PHOTOS_UNUSABLE"))
                .andExpect(jsonPath("$.rejectionNote").value("Se ven borrosas."));
    }

    /** RN-022: no se rechaza sin decir por que. */
    @Test
    void deberia_exigir_motivo_al_rechazar() throws Exception {
        mvc.perform(post("/api/v1/listings/" + UUID.randomUUID() + "/rejection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Sin motivo.\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("reason"));

        verifyNoInteractions(rechazar);
    }

    @Test
    void deberia_rechazar_un_motivo_que_no_esta_en_la_lista_cerrada() throws Exception {
        mvc.perform(post("/api/v1/listings/" + UUID.randomUUID() + "/rejection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"NO_ME_GUSTA\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"));

        verifyNoInteractions(rechazar);
    }

    /** RN-063: es moderador, y la publicacion es suya. 403 con codigo propio. */
    @Test
    void deberia_responder_403_si_el_moderador_decide_sobre_lo_suyo_criterio_24() throws Exception {
        when(aprobar.execute(any())).thenThrow(new SelfModerationForbiddenException());

        mvc.perform(post("/api/v1/listings/" + UUID.randomUUID() + "/approval"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CATALOG_SELF_MODERATION_FORBIDDEN"));
    }

    /** Criterio 34: llego tarde. La peticion esta bien y lo que no encaja es el estado. */
    @Test
    void deberia_responder_409_cuando_el_estado_ya_no_admite_la_decision() throws Exception {
        when(aprobar.execute(any()))
                .thenThrow(new InvalidListingTransitionException(ListingStatus.ARCHIVED, ListingStatus.PUBLISHED));

        mvc.perform(post("/api/v1/listings/" + UUID.randomUUID() + "/approval"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATALOG_LISTING_INVALID_STATE"));
    }

    /**
     * Criterio 31: retirar algo ya visible es del moderador y tiene su propia ruta, con
     * motivo obligatorio porque va en el correo que avisa al vendedor.
     */
    @Test
    void deberia_retirar_con_motivo_por_su_propia_ruta_criterio_31() throws Exception {
        SellerId vendedor = new SellerId(UUID.randomUUID());
        when(retirar.execute(any()))
                .thenReturn(CatalogoDelBorde.con(
                        vendedor,
                        ListingStatus.ARCHIVED,
                        ListingRejectionReason.PROHIBITED_ITEM,
                        "No se admite.",
                        java.util.Set.of()));

        mvc.perform(post("/api/v1/listings/" + UUID.randomUUID() + "/removal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"PROHIBITED_ITEM\",\"note\":\"No se admite.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        ArgumentCaptor<TakeDownListingCommand> comando = ArgumentCaptor.forClass(TakeDownListingCommand.class);
        verify(retirar).execute(comando.capture());
        assertThat(comando.getValue().motivo()).isEqualTo(ListingRejectionReason.PROHIBITED_ITEM);
    }

    @Test
    void deberia_exigir_motivo_al_retirar() throws Exception {
        mvc.perform(post("/api/v1/listings/" + UUID.randomUUID() + "/removal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(retirar);
    }

    private static co.sendik.catalog.model.Listing rechazada() {
        return CatalogoDelBorde.con(
                new SellerId(UUID.randomUUID()),
                ListingStatus.REJECTED,
                ListingRejectionReason.PHOTOS_UNUSABLE,
                "Se ven borrosas.",
                java.util.Set.of());
    }
}
