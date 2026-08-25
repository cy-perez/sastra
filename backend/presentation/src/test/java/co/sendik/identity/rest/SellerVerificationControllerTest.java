package co.sendik.identity.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.sendik.identity.dto.SubmitIdentityDocumentCommand;
import co.sendik.identity.exception.DocumentAlreadyVerifiedException;
import co.sendik.identity.model.BankAccount;
import co.sendik.identity.model.BankAccountNumber;
import co.sendik.identity.model.BankAccountType;
import co.sendik.identity.model.BankCode;
import co.sendik.identity.model.IdentityDocument;
import co.sendik.identity.model.IdentityDocumentNumber;
import co.sendik.identity.model.IdentityDocumentType;
import co.sendik.identity.model.LegalName;
import co.sendik.identity.model.RejectionReason;
import co.sendik.identity.model.SellerVerification;
import co.sendik.identity.model.SellerVerificationId;
import co.sendik.identity.model.UserId;
import co.sendik.identity.usecase.ReadSellerVerificationUseCase;
import co.sendik.identity.usecase.StartSellerVerificationUseCase;
import co.sendik.identity.usecase.SubmitBankAccountUseCase;
import co.sendik.identity.usecase.SubmitIdentityDocumentUseCase;
import co.sendik.identity.usecase.SubmitSelfieUseCase;
import co.sendik.identity.usecase.SubmitVerificationForReviewUseCase;
import co.sendik.shared.file.FileKey;
import co.sendik.shared.rest.ApiExceptionHandler;
import java.time.Instant;
import java.util.Optional;
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
 * El borde de la verificacion de vendedor. HU-002.
 *
 * <p>Lo que mas se comprueba aqui es <strong>lo que no sale</strong>: el criterio 11
 * prohibe que en cualquier respuesta aparezcan las imagenes, el numero de documento
 * completo o el de la cuenta. Una prueba que solo mirara los campos presentes daria
 * verde el dia que alguien agregue la clave del archivo al JSON.
 */
class SellerVerificationControllerTest {

    private static final Instant AHORA = Instant.parse("2026-08-21T15:00:00Z");

    private static final UserId USUARIO = UserId.nuevo();

    private static final String CEDULA = "1053812947";

    private static final String CUENTA = "91500123456";

    private static final LegalName TITULAR = new LegalName("Ana Maria Garcia");

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private final StartSellerVerificationUseCase iniciar = mock(StartSellerVerificationUseCase.class);
    private final SubmitIdentityDocumentUseCase documento = mock(SubmitIdentityDocumentUseCase.class);
    private final SubmitSelfieUseCase selfie = mock(SubmitSelfieUseCase.class);
    private final SubmitBankAccountUseCase cuenta = mock(SubmitBankAccountUseCase.class);
    private final SubmitVerificationForReviewUseCase enviar = mock(SubmitVerificationForReviewUseCase.class);
    private final ReadSellerVerificationUseCase lectura = mock(ReadSellerVerificationUseCase.class);

    private MockMvc mvc;

    /** Suple lo que en produccion pone Spring Security: el montaje autonomo no trae filtros. */
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
                    .subject(USUARIO.toString())
                    .build();
        }
    }

    private static SellerVerification completa() {
        return SellerVerification.iniciar(SellerVerificationId.nuevo(), USUARIO, AHORA)
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
                        AHORA);
    }

    @BeforeEach
    void montarElBorde() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new SellerVerificationController(iniciar, documento, selfie, cuenta, enviar, lectura))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(new TokenDePrueba())
                .build();
    }

    // --- Criterio 11: lo que no sale -----------------------------------------

    @Test
    void deberia_cumplir_el_criterio_11_sin_numeros_completos_ni_claves_de_archivo() throws Exception {
        when(lectura.execute(USUARIO)).thenReturn(Optional.of(completa()));

        MvcResult resultado = mvc.perform(get("/api/v1/users/me/verification"))
                .andExpect(status().isOk())
                .andReturn();

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
    void deberia_devolver_solo_los_cuatro_ultimos_digitos() throws Exception {
        when(lectura.execute(USUARIO)).thenReturn(Optional.of(completa()));

        mvc.perform(get("/api/v1/users/me/verification"))
                .andExpect(jsonPath("$.documentNumberLastFour").value("2947"))
                .andExpect(jsonPath("$.bankAccountLastFour").value("3456"))
                .andExpect(jsonPath("$.documentSubmitted").value(true))
                .andExpect(jsonPath("$.selfieSubmitted").value(true));
    }

    @Test
    void deberia_decir_cuantos_intentos_quedan() throws Exception {
        when(lectura.execute(USUARIO)).thenReturn(Optional.of(completa().enviarARevision(AHORA)));

        mvc.perform(get("/api/v1/users/me/verification"))
                .andExpect(jsonPath("$.attempts").value(1))
                .andExpect(jsonPath("$.remainingAttempts").value(2))
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
    }

    @Test
    void deberia_devolver_el_motivo_del_rechazo_para_que_el_cliente_lo_traduzca() throws Exception {
        when(lectura.execute(USUARIO))
                .thenReturn(Optional.of(completa()
                        .enviarARevision(AHORA)
                        .rechazar(RejectionReason.ILLEGIBLE_PHOTOS, "El reverso sale oscuro", AHORA)));

        mvc.perform(get("/api/v1/users/me/verification"))
                .andExpect(jsonPath("$.rejectionReason").value("ILLEGIBLE_PHOTOS"))
                .andExpect(jsonPath("$.rejectionNote").value("El reverso sale oscuro"));
    }

    /** El recurso todavia no existe, y eso es lo que significa un 404. */
    @Test
    void deberia_responder_404_cuando_no_ha_empezado() throws Exception {
        when(lectura.execute(USUARIO)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/users/me/verification")).andExpect(status().isNotFound());
    }

    // --- Iniciar --------------------------------------------------------------

    @Test
    void deberia_iniciar_y_responder_200_por_ser_idempotente() throws Exception {
        when(iniciar.execute(any()))
                .thenReturn(SellerVerification.iniciar(SellerVerificationId.nuevo(), USUARIO, AHORA));

        mvc.perform(post("/api/v1/users/me/verification"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.documentSubmitted").value(false));
    }

    // --- Documento ------------------------------------------------------------

    /**
     * La cuenta sale del token y nunca de la peticion. Con un identificador de la
     * peticion, cualquiera podria subir una cedula a la solicitud de otra persona.
     */
    @Test
    void deberia_tomar_la_cuenta_del_token_al_entregar_el_documento() throws Exception {
        when(documento.execute(any())).thenReturn(completa());

        mvc.perform(multipart("/api/v1/users/me/verification/document")
                        .file(new MockMultipartFile("frente", "f.png", "image/png", PNG))
                        .file(new MockMultipartFile("reverso", "r.png", "image/png", PNG))
                        .param("tipo", "CC")
                        .param("numero", CEDULA)
                        .param("titular", TITULAR.value())
                        .with(peticion -> {
                            peticion.setMethod("PUT");
                            return peticion;
                        }))
                .andExpect(status().isOk());

        ArgumentCaptor<SubmitIdentityDocumentCommand> capturado =
                ArgumentCaptor.forClass(SubmitIdentityDocumentCommand.class);
        verify(documento).execute(capturado.capture());

        assertThat(capturado.getValue().usuario()).isEqualTo(USUARIO);
        assertThat(capturado.getValue().tipo()).isEqualTo(IdentityDocumentType.CC);
        assertThat(capturado.getValue().frente()).isEqualTo(PNG);
    }

    @Test
    void deberia_aceptar_el_tipo_de_documento_en_minusculas() throws Exception {
        when(documento.execute(any())).thenReturn(completa());

        mvc.perform(multipart("/api/v1/users/me/verification/document")
                        .file(new MockMultipartFile("frente", "f.png", "image/png", PNG))
                        .file(new MockMultipartFile("reverso", "r.png", "image/png", PNG))
                        .param("tipo", "ppt")
                        .param("numero", CEDULA)
                        .param("titular", TITULAR.value())
                        .with(peticion -> {
                            peticion.setMethod("PUT");
                            return peticion;
                        }))
                .andExpect(status().isOk());

        ArgumentCaptor<SubmitIdentityDocumentCommand> capturado =
                ArgumentCaptor.forClass(SubmitIdentityDocumentCommand.class);
        verify(documento).execute(capturado.capture());

        assertThat(capturado.getValue().tipo()).isEqualTo(IdentityDocumentType.PPT);
    }

    /**
     * Con el enum en la firma del metodo, un valor desconocido sale como 500 por un
     * fallo de conversion de Spring que nadie mapea. Convertido a mano, es un error de
     * validacion, que es lo que es.
     */
    @Test
    void deberia_rechazar_un_tipo_de_documento_que_no_existe_sin_reventar() throws Exception {
        mvc.perform(multipart("/api/v1/users/me/verification/document")
                        .file(new MockMultipartFile("frente", "f.png", "image/png", PNG))
                        .file(new MockMultipartFile("reverso", "r.png", "image/png", PNG))
                        .param("tipo", "PASAPORTE")
                        .param("numero", CEDULA)
                        .param("titular", TITULAR.value())
                        .with(peticion -> {
                            peticion.setMethod("PUT");
                            return peticion;
                        }))
                .andExpect(status().is4xxClientError());
    }

    /** Criterio 5, traducido a un conflicto en el borde. */
    @Test
    void deberia_responder_409_cuando_el_documento_ya_esta_verificado() throws Exception {
        when(documento.execute(any())).thenThrow(new DocumentAlreadyVerifiedException());

        mvc.perform(multipart("/api/v1/users/me/verification/document")
                        .file(new MockMultipartFile("frente", "f.png", "image/png", PNG))
                        .file(new MockMultipartFile("reverso", "r.png", "image/png", PNG))
                        .param("tipo", "CC")
                        .param("numero", CEDULA)
                        .param("titular", TITULAR.value())
                        .with(peticion -> {
                            peticion.setMethod("PUT");
                            return peticion;
                        }))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELLER_DOCUMENT_ALREADY_VERIFIED"));
    }

    // --- Selfie y cuenta ------------------------------------------------------

    @Test
    void deberia_entregar_la_selfie() throws Exception {
        when(selfie.execute(any())).thenReturn(completa());

        mvc.perform(multipart("/api/v1/users/me/verification/selfie")
                        .file(new MockMultipartFile("archivo", "s.png", "image/png", PNG))
                        .with(peticion -> {
                            peticion.setMethod("PUT");
                            return peticion;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selfieSubmitted").value(true));
    }

    @Test
    void deberia_registrar_la_cuenta_bancaria() throws Exception {
        when(cuenta.execute(any())).thenReturn(completa());

        mvc.perform(put("/api/v1/users/me/verification/bank-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bank":"bancolombia","accountType":"SAVINGS",
                                 "accountNumber":"91500123456","holderName":"Ana Maria Garcia"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankAccountLastFour").value("3456"));
    }

    @Test
    void deberia_rechazar_una_cuenta_sin_titular() throws Exception {
        mvc.perform(put("/api/v1/users/me/verification/bank-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bank":"bancolombia","accountType":"SAVINGS",
                                 "accountNumber":"91500123456","holderName":"  "}
                                """))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void deberia_rechazar_un_numero_de_cuenta_con_letras_en_el_borde() throws Exception {
        mvc.perform(put("/api/v1/users/me/verification/bank-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bank":"bancolombia","accountType":"SAVINGS",
                                 "accountNumber":"9150ABC3456","holderName":"Ana Maria Garcia"}
                                """))
                .andExpect(status().is4xxClientError());
    }

    // --- Enviar ---------------------------------------------------------------

    @Test
    void deberia_enviar_a_revision() throws Exception {
        when(enviar.execute(any())).thenReturn(completa().enviarARevision(AHORA));

        mvc.perform(post("/api/v1/users/me/verification/submission"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
    }
}
