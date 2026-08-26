package co.sendik.catalog.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.sendik.catalog.dto.ChangeListingPriceCommand;
import co.sendik.catalog.dto.ChangeListingShippingCommand;
import co.sendik.catalog.dto.CreateListingCommand;
import co.sendik.catalog.dto.ReadListingQuery;
import co.sendik.catalog.dto.SellerListingCommand;
import co.sendik.catalog.dto.UploadListingImageCommand;
import co.sendik.catalog.exception.IncompleteListingException;
import co.sendik.catalog.exception.MeasurementsIncompleteException;
import co.sendik.catalog.exception.SellerNotEligibleException;
import co.sendik.catalog.model.ImageKind;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.model.MeasurementGroup;
import co.sendik.catalog.model.MeasurementKind;
import co.sendik.catalog.model.SellerId;
import co.sendik.catalog.usecase.ArchiveListingUseCase;
import co.sendik.catalog.usecase.ChangeListingPriceUseCase;
import co.sendik.catalog.usecase.ChangeListingShippingUseCase;
import co.sendik.catalog.usecase.CreateListingUseCase;
import co.sendik.catalog.usecase.PauseListingUseCase;
import co.sendik.catalog.usecase.ReadListingUseCase;
import co.sendik.catalog.usecase.RemoveListingImageUseCase;
import co.sendik.catalog.usecase.ReopenListingUseCase;
import co.sendik.catalog.usecase.ResumeListingUseCase;
import co.sendik.catalog.usecase.SubmitListingForReviewUseCase;
import co.sendik.catalog.usecase.UpdateListingContentUseCase;
import co.sendik.catalog.usecase.UploadListingImageUseCase;
import co.sendik.catalog.usecase.WithdrawListingUseCase;
import co.sendik.shared.file.FileKey;
import co.sendik.shared.port.out.PublicFileStore;
import co.sendik.shared.rest.ApiExceptionHandler;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * El borde de la publicacion, del lado del vendedor. HU-007.
 *
 * <p>Lo que mas se comprueba aqui son dos cosas. Una, <strong>lo que no sale</strong>:
 * quien lee una publicacion sin ser su dueno ni moderador no recibe ni la version, ni las
 * marcas de atencion, ni el motivo del rechazo. Una prueba que solo mirara los campos
 * presentes daria verde el dia que alguien los agregue a la forma publica.
 *
 * <p>Dos, <strong>que cada fallo salga con su estado</strong>. Casi todos los criterios de
 * esta historia se expresan en un codigo HTTP, y traducir mal una excepcion convierte un
 * 422 que el formulario sabe mostrar en un 500 que no dice nada.
 *
 * <p>El montaje es autonomo y no trae filtros de seguridad. Que la ruta de aprobar exija
 * rol, y que con la bandera apagada todo responda 404, se prueba en {@code bootstrap} con
 * el contexto de verdad: aqui no hay cadena de filtros que probar.
 */
class ListingsControllerTest {

    private static final SellerId VENDEDOR = new SellerId(java.util.UUID.randomUUID());

    private static final SellerId OTRO = new SellerId(java.util.UUID.randomUUID());

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};

    private final CreateListingUseCase crear = mock(CreateListingUseCase.class);
    private final ReadListingUseCase leer = mock(ReadListingUseCase.class);
    private final UpdateListingContentUseCase editar = mock(UpdateListingContentUseCase.class);
    private final ChangeListingPriceUseCase precio = mock(ChangeListingPriceUseCase.class);
    private final ChangeListingShippingUseCase envio = mock(ChangeListingShippingUseCase.class);
    private final UploadListingImageUseCase subirImagen = mock(UploadListingImageUseCase.class);
    private final RemoveListingImageUseCase borrarImagen = mock(RemoveListingImageUseCase.class);
    private final SubmitListingForReviewUseCase enviarARevision = mock(SubmitListingForReviewUseCase.class);
    private final WithdrawListingUseCase retirarDeRevision = mock(WithdrawListingUseCase.class);
    private final ReopenListingUseCase retomar = mock(ReopenListingUseCase.class);
    private final PauseListingUseCase pausar = mock(PauseListingUseCase.class);
    private final ResumeListingUseCase reanudar = mock(ResumeListingUseCase.class);
    private final ArchiveListingUseCase archivar = mock(ArchiveListingUseCase.class);
    private final PublicFileStore almacen = mock(PublicFileStore.class);

    private final TokenDePrueba token = new TokenDePrueba();

    private MockMvc mvc;

    /** Suple lo que en produccion pone Spring Security. Puede no dar token: hay una ruta publica. */
    private static final class TokenDePrueba implements HandlerMethodArgumentResolver {

        private @Nullable SellerId quien = VENDEDOR;
        private boolean moderador;

        @Override
        public boolean supportsParameter(MethodParameter parametro) {
            return Jwt.class.equals(parametro.getParameterType());
        }

        @Override
        public @Nullable Object resolveArgument(
                MethodParameter parametro,
                ModelAndViewContainer contenedor,
                NativeWebRequest peticion,
                WebDataBinderFactory fabrica) {

            if (quien == null) {
                return null;
            }
            return Jwt.withTokenValue("da-igual")
                    .header("alg", "HS256")
                    .subject(quien.value().toString())
                    .claim("roles", moderador ? List.of("MODERATOR") : List.of())
                    .build();
        }
    }

    @BeforeEach
    void montarElBorde() {
        when(almacen.direccionDe(any(FileKey.class)))
                .thenAnswer(llamada -> URI.create("https://cdn.sendik.co/"
                        + llamada.getArgument(0, FileKey.class).value()));

        mvc = MockMvcBuilders.standaloneSetup(new ListingsController(
                        crear,
                        leer,
                        editar,
                        precio,
                        envio,
                        subirImagen,
                        borrarImagen,
                        enviarARevision,
                        retirarDeRevision,
                        retomar,
                        pausar,
                        reanudar,
                        archivar,
                        almacen))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(token)
                .build();
    }

    // --- Criterio 4: crear el borrador ---------------------------------------

    @Test
    void deberia_crear_el_borrador_con_201_y_location_criterio_4() throws Exception {
        Listing borrador = CatalogoDelBorde.borrador(VENDEDOR);
        when(crear.execute(any())).thenReturn(borrador);

        mvc.perform(post("/api/v1/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDeProducto()))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                                "Location", "/api/v1/listings/" + borrador.id().value()))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    /**
     * El vendedor sale del token y no del cuerpo. Es la regla que impide publicar en
     * nombre de otra persona, y aqui es donde se puede comprobar de verdad.
     */
    @Test
    void deberia_tomar_el_vendedor_del_token_y_no_de_la_peticion() throws Exception {
        when(crear.execute(any())).thenReturn(CatalogoDelBorde.borrador(VENDEDOR));

        mvc.perform(post("/api/v1/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDeProducto()))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateListingCommand> comando = ArgumentCaptor.forClass(CreateListingCommand.class);
        verify(crear).execute(comando.capture());
        assertThat(comando.getValue().vendedor()).isEqualTo(VENDEDOR);
    }

    /** RN-011: no esta verificado. 403 y no 401, porque su sesion es valida. */
    @Test
    void deberia_responder_403_a_quien_no_esta_verificado_RN_011() throws Exception {
        when(crear.execute(any())).thenThrow(new SellerNotEligibleException());

        mvc.perform(post("/api/v1/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDeProducto()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CATALOG_SELLER_NOT_VERIFIED"));
    }

    // --- Criterio 33: quien ve que ------------------------------------------

    @Test
    void no_deberia_contar_nada_de_moderacion_a_quien_solo_mira_criterio_33() throws Exception {
        when(leer.execute(any())).thenReturn(Optional.of(CatalogoDelBorde.publicada(VENDEDOR)));
        token.quien = null;

        MvcResult resultado = mvc.perform(get("/api/v1/listings/" + java.util.UUID.randomUUID()))
                .andExpect(status().isOk())
                .andReturn();

        String cuerpo = resultado.getResponse().getContentAsString();

        assertThat(cuerpo)
                .doesNotContain("version")
                .doesNotContain("attentionReasons")
                .doesNotContain("requiresAttention")
                .doesNotContain("rejectionReason")
                .doesNotContain("rejectionNote")
                .doesNotContain("status")
                .contains(CatalogoDelBorde.TITULO);
    }

    @Test
    void deberia_contarle_todo_al_dueno() throws Exception {
        when(leer.execute(any())).thenReturn(Optional.of(CatalogoDelBorde.borrador(VENDEDOR)));

        mvc.perform(get("/api/v1/listings/" + java.util.UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(7))
                .andExpect(jsonPath("$.requiredShots").value(8));
    }

    @Test
    void deberia_contarle_todo_al_moderador_aunque_no_sea_suya() throws Exception {
        when(leer.execute(any())).thenReturn(Optional.of(CatalogoDelBorde.borrador(OTRO)));
        token.moderador = true;

        mvc.perform(get("/api/v1/listings/" + java.util.UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    /** Vacio es 404 y nunca 403: un 403 confirmaria que la publicacion existe. */
    @Test
    void deberia_responder_404_cuando_no_hay_nada_que_ensenar_criterio_33() throws Exception {
        when(leer.execute(any())).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/listings/" + java.util.UUID.randomUUID())).andExpect(status().isNotFound());
    }

    /** Sin token, la consulta va sin vendedor y sin rol: solo se responde lo publicado. */
    @Test
    void deberia_preguntar_sin_vendedor_cuando_nadie_inicio_sesion() throws Exception {
        when(leer.execute(any())).thenReturn(Optional.empty());
        token.quien = null;

        mvc.perform(get("/api/v1/listings/" + java.util.UUID.randomUUID())).andExpect(status().isNotFound());

        ArgumentCaptor<ReadListingQuery> consulta = ArgumentCaptor.forClass(ReadListingQuery.class);
        verify(leer).execute(consulta.capture());
        assertThat(consulta.getValue().quienMira()).isNull();
        assertThat(consulta.getValue().esModerador()).isFalse();
    }

    // --- Criterio 6 y 10: lo que falta, campo por campo ----------------------

    @Test
    void deberia_decir_que_campos_faltan_al_enviar_a_revision_criterio_6() throws Exception {
        when(enviarARevision.execute(any())).thenThrow(new IncompleteListingException(List.of("titulo", "precio")));

        mvc.perform(post("/api/v1/listings/" + java.util.UUID.randomUUID() + "/submission"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CATALOG_LISTING_INCOMPLETE"))
                .andExpect(jsonPath("$.errors[0].field").value("titulo"))
                .andExpect(jsonPath("$.errors[0].code").value("VALIDATION_REQUIRED"))
                .andExpect(jsonPath("$.errors[1].field").value("precio"));
    }

    /**
     * RN-021: las medidas que falten tambien son campos que faltan, y salen con el
     * mismo 422 y en la misma lista. Van con el grupo por delante porque en el
     * formulario son un campo dentro de otro.
     */
    @Test
    void deberia_decir_que_medidas_faltan_criterio_10() throws Exception {
        when(enviarARevision.execute(any()))
                .thenThrow(new MeasurementsIncompleteException(MeasurementGroup.TOP, Set.of(MeasurementKind.CHEST)));

        mvc.perform(post("/api/v1/listings/" + java.util.UUID.randomUUID() + "/submission"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("measurements.CHEST"));
    }

    // --- Criterios 8, 9 y 13: lo que el borde rechaza ------------------------

    @Test
    void deberia_rechazar_un_color_que_no_esta_en_la_lista_criterio_8() throws Exception {
        mvc.perform(post("/api/v1/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDeProducto("LIKE_NEW", "FUCSIA")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"));
    }

    @Test
    void deberia_rechazar_una_quinta_condicion_criterio_9() throws Exception {
        mvc.perform(post("/api/v1/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDeProducto("CASI_NUEVA", "BEIGE")))
                .andExpect(status().isBadRequest());
    }

    /** RN-029: el peso no tiene centavos y el precio no se redondea por el camino. */
    @Test
    void deberia_rechazar_un_precio_con_decimales_criterio_13() throws Exception {
        mvc.perform(patch("/api/v1/listings/" + java.util.UUID.randomUUID() + "/price")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":{\"amount\":185000.50,\"currency\":\"COP\"}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deberia_rechazar_una_moneda_que_no_es_la_del_catalogo() throws Exception {
        mvc.perform(patch("/api/v1/listings/" + java.util.UUID.randomUUID() + "/price")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":{\"amount\":185000,\"currency\":\"USD\"}}"))
                .andExpect(status().isBadRequest());
    }

    // --- Criterio 28: precio y envio no pasan por moderacion -----------------

    @Test
    void deberia_cambiar_solo_el_precio_por_su_propia_ruta_criterio_28() throws Exception {
        when(precio.execute(any())).thenReturn(CatalogoDelBorde.publicada(VENDEDOR));

        mvc.perform(patch("/api/v1/listings/" + java.util.UUID.randomUUID() + "/price")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":{\"amount\":120000,\"currency\":\"COP\"}}"))
                .andExpect(status().isOk());

        ArgumentCaptor<ChangeListingPriceCommand> comando = ArgumentCaptor.forClass(ChangeListingPriceCommand.class);
        verify(precio).execute(comando.capture());
        assertThat(comando.getValue().precio().amount().intValue()).isEqualTo(120_000);
    }

    @Test
    void deberia_cambiar_solo_el_envio_por_su_propia_ruta_criterio_28() throws Exception {
        when(envio.execute(any())).thenReturn(CatalogoDelBorde.publicada(VENDEDOR));

        mvc.perform(patch("/api/v1/listings/" + java.util.UUID.randomUUID() + "/shipping")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weightGrams\":900,\"lengthCm\":40,\"widthCm\":25,\"heightCm\":15}"))
                .andExpect(status().isOk());

        ArgumentCaptor<ChangeListingShippingCommand> comando =
                ArgumentCaptor.forClass(ChangeListingShippingCommand.class);
        verify(envio).execute(comando.capture());
        assertThat(comando.getValue().envio().weightGrams()).isEqualTo(900);
    }

    /** Media caja no es una caja: falta el peso y no se guarda a medias. */
    @Test
    void deberia_rechazar_un_envio_incompleto() throws Exception {
        mvc.perform(patch("/api/v1/listings/" + java.util.UUID.randomUUID() + "/shipping")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lengthCm\":40,\"widthCm\":25,\"heightCm\":15}"))
                .andExpect(status().isBadRequest());
    }

    // --- Criterios 14 y 18: las tomas ---------------------------------------

    @Test
    void deberia_subir_una_toma_con_su_posicion_criterio_14() throws Exception {
        when(subirImagen.execute(any())).thenReturn(CatalogoDelBorde.borrador(VENDEDOR));

        mvc.perform(multipart("/api/v1/listings/" + java.util.UUID.randomUUID() + "/images")
                        .file(new MockMultipartFile("archivo", "toma.jpg", "image/jpeg", JPEG))
                        .param("position", "3"))
                .andExpect(status().isCreated());

        ArgumentCaptor<UploadListingImageCommand> comando = ArgumentCaptor.forClass(UploadListingImageCommand.class);
        verify(subirImagen).execute(comando.capture());
        assertThat(comando.getValue().posicion()).isEqualTo(3);
        assertThat(comando.getValue().clase()).isEqualTo(ImageKind.SELLER_SHOT);
        assertThat(comando.getValue().desdeGaleria()).isFalse();
    }

    /** Criterio 18: lo declara el cliente y solo suma una marca; nunca quita una validacion. */
    @Test
    void deberia_pasar_la_declaracion_de_galeria_criterio_18() throws Exception {
        when(subirImagen.execute(any())).thenReturn(CatalogoDelBorde.borrador(VENDEDOR));

        mvc.perform(multipart("/api/v1/listings/" + java.util.UUID.randomUUID() + "/images")
                        .file(new MockMultipartFile("archivo", "toma.jpg", "image/jpeg", JPEG))
                        .param("position", "0")
                        .param("fromGallery", "true"))
                .andExpect(status().isCreated());

        ArgumentCaptor<UploadListingImageCommand> comando = ArgumentCaptor.forClass(UploadListingImageCommand.class);
        verify(subirImagen).execute(comando.capture());
        assertThat(comando.getValue().desdeGaleria()).isTrue();
    }

    @Test
    void deberia_rechazar_una_clase_de_imagen_que_no_existe() throws Exception {
        mvc.perform(multipart("/api/v1/listings/" + java.util.UUID.randomUUID() + "/images")
                        .file(new MockMultipartFile("archivo", "toma.jpg", "image/jpeg", JPEG))
                        .param("position", "0")
                        .param("kind", "PORTADA"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deberia_devolver_la_direccion_publica_de_cada_toma() throws Exception {
        when(leer.execute(any())).thenReturn(Optional.of(CatalogoDelBorde.borrador(VENDEDOR)));

        mvc.perform(get("/api/v1/listings/" + java.util.UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images[0].url").value("https://cdn.sendik.co/productos/clave-opaca-0.jpg"))
                .andExpect(jsonPath("$.images[0].kind").value("SELLER_SHOT"))
                .andExpect(jsonPath("$.images[0].angleDegrees").value(0));
    }

    // --- Criterios 19, 20, 23, 29 y 30: los actos sobre el estado ------------

    @Test
    void deberia_cablear_cada_acto_con_su_caso_de_uso() throws Exception {
        String id = java.util.UUID.randomUUID().toString();
        Listing cualquiera = CatalogoDelBorde.publicada(VENDEDOR);

        when(enviarARevision.execute(any())).thenReturn(cualquiera);
        when(retirarDeRevision.execute(any())).thenReturn(cualquiera);
        when(retomar.execute(any())).thenReturn(cualquiera);
        when(pausar.execute(any())).thenReturn(cualquiera);
        when(reanudar.execute(any())).thenReturn(cualquiera);
        when(archivar.execute(any())).thenReturn(cualquiera);

        mvc.perform(post("/api/v1/listings/" + id + "/submission")).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/listings/" + id + "/submission")).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/listings/" + id + "/rejection")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/listings/" + id + "/pause")).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/listings/" + id + "/pause")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/listings/" + id + "/archival")).andExpect(status().isOk());

        verify(enviarARevision).execute(new SellerListingCommand(VENDEDOR, co.sendik.catalog.model.ListingId.de(id)));
        verify(retirarDeRevision).execute(new SellerListingCommand(VENDEDOR, co.sendik.catalog.model.ListingId.de(id)));
        verify(retomar).execute(new SellerListingCommand(VENDEDOR, co.sendik.catalog.model.ListingId.de(id)));
        verify(pausar).execute(new SellerListingCommand(VENDEDOR, co.sendik.catalog.model.ListingId.de(id)));
        verify(reanudar).execute(new SellerListingCommand(VENDEDOR, co.sendik.catalog.model.ListingId.de(id)));
        verify(archivar).execute(new SellerListingCommand(VENDEDOR, co.sendik.catalog.model.ListingId.de(id)));
    }

    private static String cuerpoDeProducto() {
        return cuerpoDeProducto("LIKE_NEW", "BEIGE");
    }

    private static String cuerpoDeProducto(String condicion, String color) {
        return "{\"categoryId\":\"" + java.util.UUID.randomUUID() + "\","
                + "\"title\":\"" + CatalogoDelBorde.TITULO + "\","
                + "\"description\":\"Usada dos veces.\","
                + "\"condition\":\"" + condicion + "\","
                + "\"size\":{\"system\":\"ALPHA\",\"value\":\"M\"},"
                + "\"measurements\":{\"CHEST\":52.0},"
                + "\"price\":{\"amount\":185000,\"currency\":\"COP\"},"
                + "\"shipping\":{\"weightGrams\":600,\"lengthCm\":30,\"widthCm\":20,\"heightCm\":10},"
                + "\"color\":\"" + color + "\"}";
    }
}
