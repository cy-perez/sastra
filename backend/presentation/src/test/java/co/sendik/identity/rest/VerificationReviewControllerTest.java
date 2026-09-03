package co.sendik.identity.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.sendik.identity.dto.ApproveVerificationCommand;
import co.sendik.identity.dto.ListPendingVerificationsQuery;
import co.sendik.identity.dto.RejectVerificationCommand;
import co.sendik.identity.dto.VerificationImageContent;
import co.sendik.identity.dto.ViewVerificationImageCommand;
import co.sendik.identity.exception.SelfReviewForbiddenException;
import co.sendik.identity.exception.VerificationNotFoundException;
import co.sendik.identity.model.BankAccount;
import co.sendik.identity.model.BankAccountNumber;
import co.sendik.identity.model.BankAccountType;
import co.sendik.identity.model.BankCode;
import co.sendik.identity.model.IdentityDocument;
import co.sendik.identity.model.IdentityDocumentNumber;
import co.sendik.identity.model.IdentityDocumentType;
import co.sendik.identity.model.LegalName;
import co.sendik.identity.model.RejectionReason;
import co.sendik.identity.model.RevocationReason;
import co.sendik.identity.model.SellerVerification;
import co.sendik.identity.model.SellerVerificationId;
import co.sendik.identity.model.UserId;
import co.sendik.identity.model.VerificationImage;
import co.sendik.identity.usecase.ApproveVerificationUseCase;
import co.sendik.identity.usecase.ListPendingVerificationsUseCase;
import co.sendik.identity.usecase.ReadSellerVerificationUseCase;
import co.sendik.identity.usecase.RejectVerificationUseCase;
import co.sendik.identity.usecase.RevokeVerificationUseCase;
import co.sendik.identity.usecase.ViewVerificationImageUseCase;
import co.sendik.shared.file.FileKey;
import co.sendik.shared.rest.ApiExceptionHandler;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * El borde de la revision. HU-002, criterios 7 y 8.
 *
 * <p><strong>Aqui no se comprueba la autorizacion, y no se puede.</strong> El montaje
 * autonomo no trae la cadena de filtros ni los proxies de {@code @PreAuthorize}, asi que
 * todo responde como si quien llama fuera moderador. Que solo un moderador pueda entrar lo
 * comprueba {@code VerificationReviewSecurityTest} en {@code bootstrap}, con el contexto
 * completo y un token firmado de verdad.
 *
 * <p>Lo que se comprueba aqui es lo otro: que la bandeja no publique numeros completos,
 * que la imagen salga con su tipo y sin cachearse, y que los enums de la peticion se
 * traduzcan sin reventar.
 */
class VerificationReviewControllerTest {

    private static final Instant AHORA = Instant.parse("2026-08-21T15:00:00Z");

    private static final UserId MODERADOR = UserId.nuevo();

    private static final SellerVerificationId SOLICITUD = SellerVerificationId.nuevo();

    private static final String CEDULA = "1053812947";

    private static final String CUENTA = "91500123456";

    private static final LegalName TITULAR = new LegalName("Ana Maria Garcia");

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private final ListPendingVerificationsUseCase listado = mock(ListPendingVerificationsUseCase.class);
    private final ViewVerificationImageUseCase imagenes = mock(ViewVerificationImageUseCase.class);
    private final ApproveVerificationUseCase aprobar = mock(ApproveVerificationUseCase.class);
    private final RejectVerificationUseCase rechazar = mock(RejectVerificationUseCase.class);
    private final RevokeVerificationUseCase revocar = mock(RevokeVerificationUseCase.class);
    private final ReadSellerVerificationUseCase leer = mock(ReadSellerVerificationUseCase.class);

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
                    .subject(MODERADOR.toString())
                    .build();
        }
    }

    private static SellerVerification enRevision() {
        return enRevisionDe(UserId.nuevo());
    }

    /** La misma solicitud, con dueno elegido: es lo que distingue el caso de RN-060. */
    private static SellerVerification enRevisionDe(UserId dueno) {
        return SellerVerification.iniciar(SOLICITUD, dueno, AHORA)
                .conDocumento(
                        new IdentityDocument(
                                IdentityDocumentType.CC,
                                new IdentityDocumentNumber(CEDULA),
                                TITULAR,
                                new FileKey("documentos/frente-secreto.png"),
                                new FileKey("documentos/reverso-secreto.png")),
                        AHORA)
                .conSelfie(new FileKey("selfies/cara-secreta.png"), AHORA)
                .conCuentaBancaria(
                        new BankAccount(
                                new BankCode("bancolombia"),
                                BankAccountType.SAVINGS,
                                new BankAccountNumber(CUENTA),
                                TITULAR),
                        AHORA)
                .enviarARevision(AHORA);
    }

    @BeforeEach
    void montarElBorde() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new VerificationReviewController(listado, imagenes, aprobar, rechazar, revocar, leer))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(new TokenDePrueba())
                .build();
    }

    // --- La bandeja -----------------------------------------------------------

    @Test
    void deberia_listar_lo_que_espera_revision() throws Exception {
        when(listado.execute(any())).thenReturn(List.of(enRevision()));

        mvc.perform(get("/api/v1/verifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(SOLICITUD.toString()))
                .andExpect(jsonPath("$.items[0].attempts").value(1))
                .andExpect(jsonPath("$.items[0].documentNumberLastFour").value("2947"))
                .andExpect(jsonPath("$.items[0].bankAccountLastFour").value("3456"));
    }

    /**
     * El criterio 11 no hace excepciones por rol: el moderador tampoco recibe numeros
     * completos ni claves de archivo. Las imagenes las pide una por una, y eso queda
     * anotado.
     */
    @Test
    void deberia_cumplir_el_criterio_11_tambien_para_el_moderador() throws Exception {
        when(listado.execute(any())).thenReturn(List.of(enRevision()));

        MvcResult resultado = mvc.perform(get("/api/v1/verifications")).andReturn();
        String cuerpo = resultado.getResponse().getContentAsString();

        assertThat(cuerpo)
                .doesNotContain(CEDULA)
                .doesNotContain(CUENTA)
                .doesNotContain("documentos/")
                .doesNotContain("selfies/")
                .doesNotContain("secreto")
                .doesNotContain("secreta");
    }

    @Test
    void deberia_respetar_la_pagina_y_el_tamano_pedidos() throws Exception {
        when(listado.execute(any())).thenReturn(List.of());

        mvc.perform(get("/api/v1/verifications").param("page", "3").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(3))
                .andExpect(jsonPath("$.size").value(5));

        ArgumentCaptor<ListPendingVerificationsQuery> consulta =
                ArgumentCaptor.forClass(ListPendingVerificationsQuery.class);
        verify(listado).execute(consulta.capture());
        assertThat(consulta.getValue().pagina()).isEqualTo(3);
        assertThat(consulta.getValue().tamano()).isEqualTo(5);
    }

    /**
     * Sin desplazamiento no habia forma de llegar a la segunda pagina, y con {@code limite}
     * en espanol quien consumia esta ruta tenia que aprender una excepcion al contrato.
     *
     * <p>Esta prueba es la que sujeta las dos cosas: que el nombre del parametro es el del
     * contrato y que la pagina viaja hasta el caso de uso. Sin ella, volver a
     * {@code ?limite=} o dejar la pagina en el camino no rompe nada visible aqui.
     */
    @Test
    void deberia_ignorar_el_nombre_viejo_del_parametro() throws Exception {
        when(listado.execute(any())).thenReturn(List.of());

        mvc.perform(get("/api/v1/verifications").param("limite", "5")).andExpect(status().isOk());

        ArgumentCaptor<ListPendingVerificationsQuery> consulta =
                ArgumentCaptor.forClass(ListPendingVerificationsQuery.class);
        verify(listado).execute(consulta.capture());
        assertThat(consulta.getValue().tamano()).isEqualTo(20);
    }

    /** El tope lo pone la consulta; aqui se comprueba que llega al borde y no como 500. */
    @Test
    void deberia_rechazar_un_tamano_de_pagina_absurdo() throws Exception {
        mvc.perform(get("/api/v1/verifications").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"));
    }

    @Test
    void deberia_rechazar_una_pagina_negativa() throws Exception {
        mvc.perform(get("/api/v1/verifications").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"));
    }

    // --- Las imagenes ---------------------------------------------------------

    @Test
    void deberia_servir_la_imagen_con_su_tipo_y_sin_cachearla() throws Exception {
        when(imagenes.execute(any())).thenReturn(new VerificationImageContent(PNG, "image/png"));

        mvc.perform(get("/api/v1/verifications/" + SOLICITUD + "/images/document-front"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.IMAGE_PNG_VALUE))
                // Una cedula en la cache de un navegador compartido es justo lo que el
                // almacen reservado existe para evitar.
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    /**
     * Lo que se pide es «el frente de esta solicitud» y no una clave de archivo: con la
     * clave en la URL, quien la tuviera podria pedir cualquier cosa del almacen reservado.
     */
    @Test
    void deberia_traducir_el_nombre_de_la_imagen_a_su_accion_de_bitacora() throws Exception {
        when(imagenes.execute(any())).thenReturn(new VerificationImageContent(PNG, "image/png"));

        mvc.perform(get("/api/v1/verifications/" + SOLICITUD + "/images/selfie").param("motivo", "revision"))
                .andExpect(status().isOk());

        ArgumentCaptor<ViewVerificationImageCommand> capturado =
                ArgumentCaptor.forClass(ViewVerificationImageCommand.class);
        verify(imagenes).execute(capturado.capture());

        assertThat(capturado.getValue().imagen()).isEqualTo(VerificationImage.SELFIE);
        assertThat(capturado.getValue().moderador()).isEqualTo(MODERADOR);
        assertThat(capturado.getValue().motivo()).isEqualTo("revision");
    }

    @Test
    void deberia_rechazar_una_imagen_que_no_existe_sin_reventar() throws Exception {
        mvc.perform(get("/api/v1/verifications/" + SOLICITUD + "/images/todas")).andExpect(status().is4xxClientError());
    }

    @Test
    void deberia_responder_404_cuando_la_solicitud_no_existe() throws Exception {
        when(imagenes.execute(any())).thenThrow(new VerificationNotFoundException(SOLICITUD));

        mvc.perform(get("/api/v1/verifications/" + SOLICITUD + "/images/document-back"))
                .andExpect(status().isNotFound());
    }

    // --- Las tres decisiones --------------------------------------------------

    /**
     * El campo del que depende la mitad de interfaz de RN-060.
     *
     * <p>La cadena es: dueno de la solicitud, comparado con el del token, hasta un
     * booleano que la pantalla usa para no ofrecer la accion. El unico punto donde las dos
     * mitades se tocan es el mapeador, y sin estas dos pruebas invertir el `equals` deja
     * backend y frontend en verde con el criterio 12 roto en produccion.
     */
    @Test
    void deberia_decir_que_la_solicitud_es_propia_cuando_el_dueno_es_quien_mira() throws Exception {
        when(listado.execute(any())).thenReturn(List.of(enRevisionDe(MODERADOR)));

        mvc.perform(get("/api/v1/verifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].own").value(true));
    }

    @Test
    void deberia_decir_que_la_solicitud_es_ajena_cuando_el_dueno_es_otro() throws Exception {
        when(listado.execute(any())).thenReturn(List.of(enRevisionDe(UserId.nuevo())));

        mvc.perform(get("/api/v1/verifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].own").value(false));
    }

    /**
     * RN-060 sale como 403 y con su codigo propio.
     *
     * <p>El estado importa: 422 diria que el contenido esta mal y 409 que el estado no
     * lo admite, y no es ninguna de las dos. Lo que falla es quien lo pide.
     *
     * <p>El codigo propio importa mas. Un 403 generico le diria "no eres moderador" a
     * alguien que si lo es, y lo dejaria buscando un problema de permisos inexistente.
     */
    @Test
    void deberia_cumplir_RN_060_respondiendo_403_al_aprobar_la_propia() throws Exception {
        when(aprobar.execute(any())).thenThrow(new SelfReviewForbiddenException());

        mvc.perform(post("/api/v1/verifications/" + SOLICITUD + "/approval"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELLER_SELF_REVIEW_FORBIDDEN"));
    }

    @Test
    void deberia_aprobar_tomando_el_moderador_del_token() throws Exception {
        when(aprobar.execute(any())).thenReturn(enRevision().aprobar(AHORA));

        mvc.perform(post("/api/v1/verifications/" + SOLICITUD + "/approval"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"));

        ArgumentCaptor<ApproveVerificationCommand> capturado =
                ArgumentCaptor.forClass(ApproveVerificationCommand.class);
        verify(aprobar).execute(capturado.capture());

        assertThat(capturado.getValue().moderador()).isEqualTo(MODERADOR);
        assertThat(capturado.getValue().verificacion()).isEqualTo(SOLICITUD);
    }

    @Test
    void deberia_rechazar_con_motivo_y_nota() throws Exception {
        when(rechazar.execute(any()))
                .thenReturn(enRevision().rechazar(RejectionReason.ILLEGIBLE_PHOTOS, "Sale oscuro", AHORA));

        mvc.perform(post("/api/v1/verifications/" + SOLICITUD + "/rejection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"ILLEGIBLE_PHOTOS\",\"note\":\"Sale oscuro\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rejectionReason").value("ILLEGIBLE_PHOTOS"));

        ArgumentCaptor<RejectVerificationCommand> capturado = ArgumentCaptor.forClass(RejectVerificationCommand.class);
        verify(rechazar).execute(capturado.capture());

        assertThat(capturado.getValue().motivo()).isEqualTo(RejectionReason.ILLEGIBLE_PHOTOS);
        assertThat(capturado.getValue().nota()).isEqualTo("Sale oscuro");
    }

    @Test
    void deberia_rechazar_un_motivo_que_no_esta_en_la_lista_cerrada() throws Exception {
        mvc.perform(post("/api/v1/verifications/" + SOLICITUD + "/rejection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"NO_ME_CAE_BIEN\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void deberia_exigir_un_motivo_al_rechazar() throws Exception {
        mvc.perform(post("/api/v1/verifications/" + SOLICITUD + "/rejection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"sin motivo\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void deberia_revocar_el_sello() throws Exception {
        when(revocar.execute(any()))
                .thenReturn(enRevision().aprobar(AHORA).revocar(RevocationReason.DOCUMENT_NOT_ITS_HOLDER, null, AHORA));

        mvc.perform(post("/api/v1/verifications/" + SOLICITUD + "/revocation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"DOCUMENT_NOT_ITS_HOLDER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"));
    }

    // --- RN-069 --------------------------------------------------------------

    /**
     * Las dos listas cerradas no son intercambiables, y el borde es donde se nota.
     *
     * <p>{@code REQUIREMENTS_NOT_MET} es un motivo de rechazo perfectamente valido y no
     * existe en la lista de revocacion. Antes de HU-010 este cuerpo se aceptaba, porque el
     * endpoint reutilizaba la enumeracion del rechazo.
     */
    @Test
    void deberia_cumplir_RN_069_rechazando_un_motivo_de_la_lista_del_rechazo() throws Exception {
        mvc.perform(post("/api/v1/verifications/" + SOLICITUD + "/revocation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"REQUIREMENTS_NOT_MET\"}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void deberia_exigir_un_motivo_al_revocar() throws Exception {
        mvc.perform(post("/api/v1/verifications/" + SOLICITUD + "/revocation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"sin motivo\"}"))
                .andExpect(status().is4xxClientError());
    }

    // --- HU-010: de un vendedor a su verificacion ----------------------------

    @Test
    void deberia_dar_la_verificacion_de_un_vendedor_por_su_cuenta() throws Exception {
        when(leer.execute(any())).thenReturn(Optional.of(enRevision().aprobar(AHORA)));

        mvc.perform(get("/api/v1/verifications/by-seller/" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    /**
     * Dos campos y ni uno mas.
     *
     * <p>Lo que esta ruta existe para contestar es "hay sello que revocar, y sobre que
     * identificador". Si algun dia devuelve el documento o la cuenta, esta prueba lo dice:
     * sin ella, un campo de mas se cuela y nadie lo nota hasta la auditoria.
     */
    @Test
    void deberia_responder_solo_el_identificador_y_el_estado() throws Exception {
        when(leer.execute(any())).thenReturn(Optional.of(enRevision().aprobar(AHORA)));

        mvc.perform(get("/api/v1/verifications/by-seller/" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$.documentNumberLastFour").doesNotExist())
                .andExpect(jsonPath("$.bankAccount").doesNotExist());
    }

    @Test
    void deberia_responder_404_cuando_esa_persona_nunca_empezo() throws Exception {
        when(leer.execute(any())).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/verifications/by-seller/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }
}
