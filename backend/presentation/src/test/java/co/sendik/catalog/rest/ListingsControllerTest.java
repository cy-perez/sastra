package co.sendik.catalog.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import co.sendik.catalog.dto.ReadModerationHistoryQuery;
import co.sendik.catalog.dto.RemoveListingImageCommand;
import co.sendik.catalog.dto.SellerListingCommand;
import co.sendik.catalog.dto.UpdateListingContentCommand;
import co.sendik.catalog.dto.UploadListingImageCommand;
import co.sendik.catalog.exception.ConditionNotAllowedException;
import co.sendik.catalog.exception.IncompleteListingException;
import co.sendik.catalog.exception.ListingNotFoundException;
import co.sendik.catalog.exception.MeasurementsIncompleteException;
import co.sendik.catalog.exception.ReferenceImageNotAllowedException;
import co.sendik.catalog.exception.SellerNotEligibleException;
import co.sendik.catalog.model.AttentionReason;
import co.sendik.catalog.model.Condition;
import co.sendik.catalog.model.ImageKind;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.model.ListingId;
import co.sendik.catalog.model.ListingRejectionReason;
import co.sendik.catalog.model.ListingStatus;
import co.sendik.catalog.model.MeasurementGroup;
import co.sendik.catalog.model.MeasurementKind;
import co.sendik.catalog.model.ModerationAction;
import co.sendik.catalog.model.ModerationEvent;
import co.sendik.catalog.model.SellerId;
import co.sendik.catalog.usecase.ArchiveListingUseCase;
import co.sendik.catalog.usecase.ChangeListingPriceUseCase;
import co.sendik.catalog.usecase.ChangeListingShippingUseCase;
import co.sendik.catalog.usecase.CreateListingUseCase;
import co.sendik.catalog.usecase.PauseListingUseCase;
import co.sendik.catalog.usecase.ReadListingUseCase;
import co.sendik.catalog.usecase.ReadModerationHistoryUseCase;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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

    private static final Instant CUANDO = Instant.parse("2026-09-04T15:00:00Z");

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
    private final ReadModerationHistoryUseCase rastro = mock(ReadModerationHistoryUseCase.class);
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
            return token();
        }

        private Jwt token() {
            return Jwt.withTokenValue("da-igual")
                    .header("alg", "HS256")
                    .subject(quien == null ? "sin-sesion" : quien.value().toString())
                    .claim("roles", moderador ? List.of("MODERATOR") : List.of())
                    .build();
        }

        /**
         * La autenticacion, como la entrega Spring Security en produccion.
         *
         * <p>No sale de este resolvedor: un parametro {@code Authentication} lo resuelve
         * {@code ServletRequestMethodArgumentResolver}, que es interno y va antes que los
         * propios, leyendo {@code request.getUserPrincipal()}. Por eso las peticiones que
         * la necesitan la ponen como principal.
         *
         * <p>Las autoridades llevan el prefijo {@code ROLE_}, que es el que pone el
         * convertidor de {@code SecurityConfig}: un doble con otro prefijo daria verde
         * sobre un controlador roto.
         */
        private @Nullable Authentication autenticacion() {
            if (quien == null) {
                return null;
            }
            return new JwtAuthenticationToken(
                    token(), moderador ? List.of(new SimpleGrantedAuthority("ROLE_MODERATOR")) : List.of());
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
                        rastro,
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

        MvcResult resultado = mvc.perform(leer(java.util.UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andReturn();

        String cuerpo = resultado.getResponse().getContentAsString();

        assertThat(cuerpo)
                // Lo que si tiene que estar. Sin esto, un cuerpo vacio pasaria todas las
                // ausencias de abajo y la prueba diria que nada se filtra.
                .contains(CatalogoDelBorde.TITULO)
                .contains("\"id\"")
                .contains("\"images\"")
                // Y la cocina de la moderacion, que no.
                .doesNotContain("version")
                .doesNotContain("attentionReasons")
                .doesNotContain("requiresAttention")
                .doesNotContain("rejectionReason")
                .doesNotContain("rejectionNote")
                .doesNotContain("status");
    }

    @Test
    void deberia_contarle_todo_al_dueno() throws Exception {
        when(leer.execute(any())).thenReturn(Optional.of(CatalogoDelBorde.borrador(VENDEDOR)));

        mvc.perform(leer(java.util.UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(7))
                .andExpect(jsonPath("$.requiredShots").value(8));
    }

    @Test
    void deberia_contarle_todo_al_moderador_aunque_no_sea_suya() throws Exception {
        when(leer.execute(any())).thenReturn(Optional.of(CatalogoDelBorde.borrador(OTRO)));
        token.moderador = true;

        mvc.perform(leer(java.util.UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    /** Vacio es 404 y nunca 403: un 403 confirmaria que la publicacion existe. */
    @Test
    void deberia_responder_404_cuando_no_hay_nada_que_ensenar_criterio_33() throws Exception {
        when(leer.execute(any())).thenReturn(Optional.empty());

        mvc.perform(leer(java.util.UUID.randomUUID().toString())).andExpect(status().isNotFound());
    }

    /** Sin token, la consulta va sin vendedor y sin rol: solo se responde lo publicado. */
    @Test
    void deberia_preguntar_sin_vendedor_cuando_nadie_inicio_sesion() throws Exception {
        when(leer.execute(any())).thenReturn(Optional.empty());
        token.quien = null;

        mvc.perform(leer(java.util.UUID.randomUUID().toString())).andExpect(status().isNotFound());

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
                .andExpect(jsonPath("$.errors[0].field").value("title"))
                .andExpect(jsonPath("$.errors[0].code").value("VALIDATION_REQUIRED"))
                .andExpect(jsonPath("$.errors[1].field").value("price"));
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
    }

    /**
     * Omitir la marca vale por "desde la galeria", que es la lectura conservadora.
     *
     * <p>Hasta HU-003 el unico cliente la mandaba siempre en verdadero y este valor por
     * omision no se ejercia nunca. Desde que existe el asistente de captura hay quien manda
     * falso, y entonces **omitir el parametro pasa a equivaler a declarar "esto lo tome con
     * la camara"** sin que nadie pueda desmentirlo. Un cliente viejo en cache, un script o
     * alguien curioso no consiguen asi quitarse la marca de atencion.
     *
     * <p>Estaba fijado de refilon dentro de la prueba de la posicion, que es donde un
     * cambio de criterio pasa inadvertido. Aqui es lo unico que se afirma.
     */
    @Test
    void deberia_suponer_galeria_cuando_no_se_declara_el_origen() throws Exception {
        when(subirImagen.execute(any())).thenReturn(CatalogoDelBorde.borrador(VENDEDOR));

        mvc.perform(multipart("/api/v1/listings/" + java.util.UUID.randomUUID() + "/images")
                        .file(new MockMultipartFile("archivo", "toma.jpg", "image/jpeg", JPEG))
                        .param("position", "0"))
                .andExpect(status().isCreated());

        ArgumentCaptor<UploadListingImageCommand> comando = ArgumentCaptor.forClass(UploadListingImageCommand.class);
        verify(subirImagen).execute(comando.capture());
        assertThat(comando.getValue().desdeGaleria()).isTrue();
    }

    /** Y el asistente de captura si puede declarar que la tomo con la camara (HU-003). */
    @Test
    void deberia_aceptar_que_la_toma_vino_de_la_camara() throws Exception {
        when(subirImagen.execute(any())).thenReturn(CatalogoDelBorde.borrador(VENDEDOR));

        mvc.perform(multipart("/api/v1/listings/" + java.util.UUID.randomUUID() + "/images")
                        .file(new MockMultipartFile("archivo", "toma.jpg", "image/jpeg", JPEG))
                        .param("position", "0")
                        .param("fromGallery", "false"))
                .andExpect(status().isCreated());

        ArgumentCaptor<UploadListingImageCommand> comando = ArgumentCaptor.forClass(UploadListingImageCommand.class);
        verify(subirImagen).execute(comando.capture());
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

        mvc.perform(leer(java.util.UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images[0].url").value("https://cdn.sendik.co/productos/clave-opaca-0.jpg"))
                .andExpect(jsonPath("$.images[0].kind").value("SELLER_SHOT"))
                .andExpect(jsonPath("$.images[0].angleDegrees").value(0));
    }

    // --- Criterios 5 y 27: guardar lo que lleva ------------------------------

    /**
     * Criterio 5: se guarda a medias y sigue en borrador.
     *
     * <p>El cuerpo trae solo la categoria y el titulo. Que el resto pueda faltar es la
     * mitad del criterio; la otra mitad —que al volver este lo que se dejo— la prueba el
     * caso de uso, que es quien guarda.
     */
    @Test
    void deberia_guardar_un_borrador_a_medias_criterio_5() throws Exception {
        when(editar.execute(any())).thenReturn(CatalogoDelBorde.borrador(VENDEDOR));

        mvc.perform(patch("/api/v1/listings/" + java.util.UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":\"" + java.util.UUID.randomUUID() + "\",\"title\":\"Va a medias\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        ArgumentCaptor<UpdateListingContentCommand> comando =
                ArgumentCaptor.forClass(UpdateListingContentCommand.class);
        verify(editar).execute(comando.capture());
        assertThat(comando.getValue().datos().titulo().value()).isEqualTo("Va a medias");
        assertThat(comando.getValue().datos().precio()).isNull();
        assertThat(comando.getValue().vendedor()).isEqualTo(VENDEDOR);
    }

    /** Criterio 27: editar contenido de una viva la devuelve a moderacion. */
    @Test
    void deberia_devolver_a_revision_al_editar_una_publicada_criterio_27() throws Exception {
        when(editar.execute(any()))
                .thenReturn(CatalogoDelBorde.con(VENDEDOR, ListingStatus.PENDING_REVIEW, null, null, Set.of()));

        mvc.perform(patch("/api/v1/listings/" + java.util.UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDeProducto()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
    }

    @Test
    void deberia_rechazar_una_edicion_sin_categoria() throws Exception {
        mvc.perform(patch("/api/v1/listings/" + java.util.UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Sin categoria\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("categoryId"));

        verifyNoInteractions(editar);
    }

    @Test
    void deberia_borrar_una_toma_por_su_identificador() throws Exception {
        String id = java.util.UUID.randomUUID().toString();
        String imagen = java.util.UUID.randomUUID().toString();
        when(borrarImagen.execute(any())).thenReturn(CatalogoDelBorde.borrador(VENDEDOR));

        mvc.perform(delete("/api/v1/listings/" + id + "/images/" + imagen)).andExpect(status().isOk());

        ArgumentCaptor<RemoveListingImageCommand> comando = ArgumentCaptor.forClass(RemoveListingImageCommand.class);
        verify(borrarImagen).execute(comando.capture());
        assertThat(comando.getValue().imagen().value().toString()).isEqualTo(imagen);
        assertThat(comando.getValue().vendedor()).isEqualTo(VENDEDOR);
    }

    // --- Criterios 12 y 35 a 42: tecnologia y marcas de atencion -------------

    /**
     * {@code isSealed} es el caso donde Jackson puede desayunarse el nombre.
     *
     * <p>Un componente de record llamado {@code isSealed} tiene accesor {@code isSealed()},
     * y ahi es donde una convencion de nombres de propiedad puede serializar {@code sealed}
     * y deserializar {@code isSealed}. Esta prueba lo recorre en las dos direcciones.
     */
    @Test
    void deberia_llevar_y_traer_sellado_y_garantia_criterios_36_y_42() throws Exception {
        when(crear.execute(any())).thenReturn(CatalogoDelBorde.tecnologiaSellada(VENDEDOR));

        mvc.perform(post("/api/v1/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":\"" + java.util.UUID.randomUUID() + "\","
                                + "\"title\":\"Telefono nuevo sellado\","
                                + "\"condition\":\"NEW\","
                                + "\"isSealed\":true,"
                                + "\"warrantyMonths\":12}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.product.isSealed").value(true))
                .andExpect(jsonPath("$.product.warrantyMonths").value(12));

        ArgumentCaptor<CreateListingCommand> comando = ArgumentCaptor.forClass(CreateListingCommand.class);
        verify(crear).execute(comando.capture());
        assertThat(comando.getValue().datos().sellado()).isTrue();
        assertThat(comando.getValue().datos().garantia().value()).isEqualTo(12);
    }

    /** Criterio 37: la sellada exige cuatro tomas y no ocho. Sale calculado del dominio. */
    @Test
    void deberia_decir_que_una_sellada_exige_cuatro_tomas_criterio_37() throws Exception {
        when(leer.execute(any())).thenReturn(Optional.of(CatalogoDelBorde.tecnologiaSellada(VENDEDOR)));

        mvc.perform(leer(java.util.UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiredShots").value(4));
    }

    /** RN-064: la categoria no admite lo usado. 422, no 500. */
    @Test
    void deberia_traducir_la_condicion_no_admitida_criterio_35() throws Exception {
        when(crear.execute(any())).thenThrow(new ConditionNotAllowedException(Condition.GOOD));

        mvc.perform(post("/api/v1/listings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDeProducto()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CATALOG_CONDITION_NOT_ALLOWED"));
    }

    /** RN-066: imagen de referencia sobre algo que no es tecnologia sellada. 422, no 500. */
    @Test
    void deberia_traducir_la_referencia_no_admitida_criterio_39() throws Exception {
        when(subirImagen.execute(any())).thenThrow(new ReferenceImageNotAllowedException("no esta sellada"));

        mvc.perform(multipart("/api/v1/listings/" + java.util.UUID.randomUUID() + "/images")
                        .file(new MockMultipartFile("archivo", "ref.jpg", "image/jpeg", JPEG))
                        .param("position", "0")
                        .param("kind", "REFERENCE"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CATALOG_REFERENCE_IMAGE_NOT_ALLOWED"));
    }

    /** RN-066: cada imagen de referencia va rotulada, y para eso el borde tiene que decirlo. */
    @Test
    void deberia_rotular_las_imagenes_de_referencia_criterio_41() throws Exception {
        when(leer.execute(any())).thenReturn(Optional.of(CatalogoDelBorde.tecnologiaSellada(VENDEDOR)));

        mvc.perform(leer(java.util.UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.images[?(@.kind == 'REFERENCE')]").isNotEmpty())
                .andExpect(jsonPath("$.images[?(@.kind == 'SELLER_SHOT')]").isNotEmpty());
    }

    /** Criterio 12: el precio fuera de rango se marca, y el moderador tiene que verlo. */
    @Test
    void deberia_publicar_la_marca_de_atencion_criterio_12() throws Exception {
        when(leer.execute(any()))
                .thenReturn(Optional.of(CatalogoDelBorde.con(
                        VENDEDOR,
                        ListingStatus.PENDING_REVIEW,
                        null,
                        null,
                        Set.of(AttentionReason.PRICE_OUT_OF_RANGE))));

        mvc.perform(leer(java.util.UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresAttention").value(true))
                .andExpect(jsonPath("$.attentionReasons[0]").value("PRICE_OUT_OF_RANGE"));
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

    // --- El rastro de moderacion. HU-013 -------------------------------------

    /** Criterios 1 a 3: cada paso con su accion, su motivo cuando lo hay y su fecha. */
    @Test
    void deberia_devolver_el_rastro_de_lo_mio_HU_013() throws Exception {
        String id = UUID.randomUUID().toString();
        when(rastro.execute(any()))
                .thenReturn(List.of(
                        new ModerationEvent(ModerationAction.APPROVED, null, CUANDO),
                        new ModerationEvent(ModerationAction.REJECTED, ListingRejectionReason.PHOTOS_UNUSABLE, CUANDO),
                        new ModerationEvent(ModerationAction.SUBMITTED, null, CUANDO)));

        mvc.perform(get("/api/v1/listings/" + id + "/moderation-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events.length()").value(3))
                .andExpect(jsonPath("$.events[0].action").value("APPROVED"))
                .andExpect(jsonPath("$.events[0].reason").doesNotExist())
                .andExpect(jsonPath("$.events[1].action").value("REJECTED"))
                .andExpect(jsonPath("$.events[1].reason").value("PHOTOS_UNUSABLE"))
                .andExpect(jsonPath("$.events[2].action").value("SUBMITTED"));

        // El vendedor sale del token y nunca del parametro. Es lo que impide pedir el
        // rastro de otra persona cambiando un identificador en la direccion.
        ArgumentCaptor<ReadModerationHistoryQuery> consulta = ArgumentCaptor.forClass(ReadModerationHistoryQuery.class);
        verify(rastro).execute(consulta.capture());
        assertThat(consulta.getValue().vendedor()).isEqualTo(VENDEDOR);
        assertThat(consulta.getValue().publicacion().value()).hasToString(id);
    }

    /**
     * Criterio 5 y RN-074, en el unico sitio donde se puede comprobar de verdad: el JSON.
     *
     * <p>Es lo unico que impide que el actor y la nota vuelvan por descuido. Las tres
     * barreras -la consulta, el tipo de dominio y el DTO- son invisibles desde fuera; esta
     * prueba mira lo que sale por el cable y falla si alguna cede.
     */
    @Test
    void no_deberia_decir_nunca_quien_decidio_ni_la_nota_interna_criterio_5() throws Exception {
        when(rastro.execute(any()))
                .thenReturn(List.of(new ModerationEvent(
                        ModerationAction.REJECTED, ListingRejectionReason.PHOTOS_UNUSABLE, CUANDO)));

        String cuerpo = mvc.perform(get("/api/v1/listings/" + UUID.randomUUID() + "/moderation-history"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(cuerpo)
                .doesNotContain("actor")
                .doesNotContain("moderator")
                .doesNotContain("note")
                .doesNotContain("notes");
    }

    /** Criterio 6: un borrador que nunca salio responde 200 con la lista vacia, no 404. */
    @Test
    void deberia_devolver_una_lista_vacia_cuando_no_ha_pasado_nada_criterio_6() throws Exception {
        when(rastro.execute(any())).thenReturn(List.of());

        mvc.perform(get("/api/v1/listings/" + UUID.randomUUID() + "/moderation-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events").isEmpty());
    }

    /**
     * Criterio 7: sobre una publicacion ajena es 404 y no 403.
     *
     * <p>El borde no elige: el caso de uso lanza lo mismo cuando no existe y cuando no es
     * tuya, asi que no tiene con que distinguirlas ni aunque quisiera. Lo que esta prueba
     * fija es que esa excepcion sale como 404 y no como otra cosa.
     */
    @Test
    void deberia_responder_404_y_no_403_sobre_una_publicacion_ajena_criterio_7() throws Exception {
        when(rastro.execute(any())).thenThrow(new ListingNotFoundException(ListingId.nuevo()));

        mvc.perform(get("/api/v1/listings/" + UUID.randomUUID() + "/moderation-history"))
                .andExpect(status().isNotFound())
                // El mismo codigo con el que responde una publicacion que no existe, que es
                // justo lo que el criterio 7 quiere: los dos casos son indistinguibles.
                .andExpect(jsonPath("$.code").value("COMMON_NOT_FOUND"));
    }

    /**
     * Un GET de una publicacion, con su principal cuando hay sesion.
     *
     * <p>Es la unica ruta que mira la autenticacion, porque es la unica que decide con
     * ella que forma de respuesta devuelve.
     */
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder leer(String id) {
        var peticion = get("/api/v1/listings/" + id);
        Authentication quienMira = token.autenticacion();

        return quienMira == null ? peticion : peticion.principal(quienMira);
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
