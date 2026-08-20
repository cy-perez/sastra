package co.sastra.identity.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.sastra.identity.dto.ActiveSession;
import co.sastra.identity.dto.CloseAccountCommand;
import co.sastra.identity.dto.RequestEmailChangeCommand;
import co.sastra.identity.dto.UpdateProfileCommand;
import co.sastra.identity.dto.UserDataExport;
import co.sastra.identity.exception.CloseConfirmationMismatchException;
import co.sastra.identity.model.BirthDate;
import co.sastra.identity.model.City;
import co.sastra.identity.model.DisplayName;
import co.sastra.identity.model.Email;
import co.sastra.identity.model.Phone;
import co.sastra.identity.model.TokenFamilyId;
import co.sastra.identity.model.User;
import co.sastra.identity.model.UserId;
import co.sastra.identity.model.UserLocale;
import co.sastra.identity.usecase.CloseAccountUseCase;
import co.sastra.identity.usecase.ExportUserDataUseCase;
import co.sastra.identity.usecase.ListSessionsUseCase;
import co.sastra.identity.usecase.ReadProfileUseCase;
import co.sastra.identity.usecase.RequestEmailChangeUseCase;
import co.sastra.identity.usecase.RequestEmailVerificationUseCase;
import co.sastra.identity.usecase.RevokeSessionUseCase;
import co.sastra.identity.usecase.UpdateProfileUseCase;
import co.sastra.shared.rest.ApiExceptionHandler;
import co.sastra.shared.rest.RefreshCookies;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.MethodParameter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Lo que una persona puede hacer sobre su propia cuenta. Criterios 17, 22 y 23.
 *
 * <p>Comprueba sobre todo lo que no debe salir: la IP en la lista de sesiones, y
 * cualquier hash en el archivo de datos.
 */
class UsersControllerTest {

    private static final Instant AHORA = Instant.parse("2026-08-17T15:00:00Z");
    private static final UserId USUARIO = UserId.nuevo();
    private static final TokenFamilyId LA_DE_AHORA = TokenFamilyId.nueva();

    private final RequestEmailVerificationUseCase reenvio = mock(RequestEmailVerificationUseCase.class);
    private final ListSessionsUseCase listado = mock(ListSessionsUseCase.class);
    private final RevokeSessionUseCase revocacion = mock(RevokeSessionUseCase.class);
    private final ExportUserDataUseCase exportacion = mock(ExportUserDataUseCase.class);
    private final CloseAccountUseCase cierre = mock(CloseAccountUseCase.class);
    private final ReadProfileUseCase lectura = mock(ReadProfileUseCase.class);
    private final UpdateProfileUseCase perfil = mock(UpdateProfileUseCase.class);
    private final RequestEmailChangeUseCase cambioDeCorreo = mock(RequestEmailChangeUseCase.class);

    private MockMvc mvc;

    /**
     * Suple lo que en produccion pone Spring Security. El montaje autonomo no trae
     * la cadena de filtros, asi que el token del principal se inyecta aqui con el
     * mismo {@code sub} y el mismo {@code sid} que tendria uno real.
     */
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
                    .claim("sid", LA_DE_AHORA.toString())
                    .build();
        }
    }

    @BeforeEach
    void montarElBorde() {
        UsersController controlador = new UsersController(
                reenvio,
                listado,
                revocacion,
                exportacion,
                cierre,
                lectura,
                perfil,
                cambioDeCorreo,
                new RefreshCookies("sastra_refresh", "/api/v1/auth", true, Duration.ofDays(30)));

        mvc = MockMvcBuilders.standaloneSetup(controlador)
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(new TokenDePrueba())
                .build();
    }

    // Criterio 17: la lista marca cual es la sesion desde la que se mira.
    @Test
    void deberia_listar_las_sesiones_marcando_la_actual_criterio_17() throws Exception {
        when(listado.execute(any(), any()))
                .thenReturn(List.of(
                        new ActiveSession(LA_DE_AHORA.toString(), "Chrome", AHORA, AHORA.plusSeconds(60), true),
                        new ActiveSession(
                                UUID.randomUUID().toString(), "Firefox", AHORA, AHORA.plusSeconds(60), false)));

        mvc.perform(get("/api/v1/users/me/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userAgent").value("Chrome"))
                .andExpect(jsonPath("$[0].current").value(true))
                .andExpect(jsonPath("$[1].current").value(false));

        verify(listado).execute(USUARIO, LA_DE_AHORA);
    }

    /**
     * La IP se guarda para reconocer patrones de ataque, no para ensenarsela a
     * nadie (docs/operacion/datos-personales.md).
     */
    @Test
    void nunca_deberia_devolver_la_ip_en_la_lista_de_sesiones() throws Exception {
        when(listado.execute(any(), any()))
                .thenReturn(List.of(
                        new ActiveSession(LA_DE_AHORA.toString(), "Chrome", AHORA, AHORA.plusSeconds(60), true)));

        MvcResult resultado = mvc.perform(get("/api/v1/users/me/sessions")).andReturn();

        assertThat(resultado.getResponse().getContentAsString()).doesNotContain("ip");
    }

    /**
     * Cerrar la propia sesion borra tambien la cookie: dejarla puesta apuntando a
     * una sesion revocada solo daria un 401 en el siguiente refresco.
     */
    @Test
    void deberia_borrar_la_cookie_al_cerrar_la_sesion_actual_criterio_17() throws Exception {
        MvcResult resultado = mvc.perform(delete("/api/v1/users/me/sessions/" + LA_DE_AHORA))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(resultado.getResponse().getHeader("Set-Cookie")).contains("Max-Age=0");
        verify(revocacion).execute(USUARIO, LA_DE_AHORA);
    }

    @Test
    void no_deberia_tocar_la_cookie_al_cerrar_otra_sesion() throws Exception {
        String otra = UUID.randomUUID().toString();

        mvc.perform(delete("/api/v1/users/me/sessions/" + otra))
                .andExpect(status().isNoContent())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    /**
     * Criterio 22: el archivo se descarga y no se cachea. Lleva datos personales,
     * asi que ninguna cache intermedia debe quedarse con una copia.
     */
    @Test
    void deberia_entregar_los_datos_como_descarga_sin_cache_criterio_22() throws Exception {
        when(exportacion.execute(USUARIO))
                .thenReturn(new UserDataExport(
                        AHORA,
                        new UserDataExport.Cuenta(
                                USUARIO.toString(),
                                "ana@correo.co",
                                "Ana Maria",
                                LocalDate.of(1990, 3, 4),
                                "Medellin",
                                "+57 300 000 0000",
                                "es",
                                "ACTIVE",
                                true,
                                AHORA,
                                List.of("BUYER"),
                                AHORA),
                        List.of(new UserDataExport.Consentimiento("PRIVACY", "2026-08-01", AHORA)),
                        List.of()));

        MvcResult resultado = mvc.perform(get("/api/v1/users/me/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"sastra-mis-datos.json\""))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.cuenta.correo").value("ana@correo.co"))
                // Ciudad y telefono salen en el archivo: son datos de la persona,
                // no del sistema, y el derecho a conocer los alcanza.
                .andExpect(jsonPath("$.cuenta.ciudad").value("Medellin"))
                .andExpect(jsonPath("$.cuenta.telefono").value("+57 300 000 0000"))
                // La evidencia con su version: es lo que prueba a que dijo que si.
                .andExpect(jsonPath("$.consentimientos[0].version").value("2026-08-01"))
                .andReturn();

        // Ni el hash de la contrasena ni el de ningun token: son secretos del
        // sistema, no datos de la persona.
        assertThat(resultado.getResponse().getContentAsString()).doesNotContain("hash");
    }

    @Test
    void deberia_cerrar_la_cuenta_y_borrar_la_cookie_criterio_23() throws Exception {
        MvcResult resultado = mvc.perform(delete("/api/v1/users/me")
                        .contentType("application/json")
                        .content("""
                                {"confirmation":"ana@correo.co"}
                                """))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(resultado.getResponse().getHeader("Set-Cookie")).contains("Max-Age=0");

        ArgumentCaptor<CloseAccountCommand> comando = ArgumentCaptor.forClass(CloseAccountCommand.class);
        verify(cierre).execute(comando.capture());
        assertThat(comando.getValue().usuario()).isEqualTo(USUARIO);
        assertThat(comando.getValue().confirmacion()).isEqualTo("ana@correo.co");
    }

    // Cerrar no se deshace: la confirmacion es lo unico que separa un clic mal
    // dado de perder el acceso.
    @Test
    void deberia_rechazar_el_cierre_sin_confirmacion_criterio_23() throws Exception {
        mvc.perform(delete("/api/v1/users/me").contentType("application/json").content("""
                                {"confirmation":"  "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"));

        verify(cierre, never()).execute(any());
    }

    @Test
    void deberia_traducir_una_confirmacion_que_no_coincide_criterio_23() throws Exception {
        doThrow(new CloseConfirmationMismatchException()).when(cierre).execute(any());

        mvc.perform(delete("/api/v1/users/me").contentType("application/json").content("""
                                {"confirmation":"otra@correo.co"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_CLOSE_CONFIRMATION_MISMATCH"));
    }

    @Test
    void deberia_aceptar_el_reenvio_de_verificacion_con_202_criterio_13() throws Exception {
        mvc.perform(post("/api/v1/users/me/email-verification")).andExpect(status().isAccepted());

        verify(reenvio).execute(any());
    }

    /** Una cuenta con el perfil ya puesto, para las pruebas del criterio 21. */
    private static User conPerfil(@Nullable String ciudad, @Nullable String telefono) {
        return User.registrar(
                        USUARIO,
                        new Email("ana@correo.co"),
                        new DisplayName("Ana Maria"),
                        new BirthDate(LocalDate.of(1990, 3, 4)),
                        UserLocale.ES,
                        LocalDate.of(2026, 8, 18),
                        AHORA)
                .conPerfil(
                        new DisplayName("Ana Maria"),
                        ciudad == null ? null : new City(ciudad),
                        telefono == null ? null : new Phone(telefono));
    }

    @Test
    void deberia_devolver_el_perfil_criterio_21() throws Exception {
        when(lectura.execute(USUARIO)).thenReturn(conPerfil("Medellin", "3001234567"));

        mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Ana Maria"))
                .andExpect(jsonPath("$.city").value("Medellin"))
                .andExpect(jsonPath("$.phone").value("3001234567"))
                .andExpect(jsonPath("$.email").value("ana@correo.co"))
                .andExpect(jsonPath("$.emailVerified").value(false));
    }

    /**
     * Devuelve como quedo, no un 204: el telefono entra con separadores y sale
     * normalizado, y el cliente no tiene por que reproducir esa regla.
     */
    @Test
    void deberia_guardar_el_perfil_y_devolver_lo_normalizado_criterio_21() throws Exception {
        when(perfil.execute(any())).thenReturn(conPerfil("Medellin", "+573001234567"));

        mvc.perform(put("/api/v1/users/me").contentType("application/json").content("""
                        {"displayName":"Ana Maria","city":"Medellin","phone":"+57 300 123 4567"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+573001234567"));

        ArgumentCaptor<UpdateProfileCommand> comando = ArgumentCaptor.forClass(UpdateProfileCommand.class);
        verify(perfil).execute(comando.capture());
        // El usuario sale del token, nunca del cuerpo.
        assertThat(comando.getValue().usuario()).isEqualTo(USUARIO);
        assertThat(comando.getValue().city()).isEqualTo("Medellin");
    }

    @Test
    void deberia_dejar_quitar_la_ciudad_y_el_telefono_criterio_21() throws Exception {
        when(perfil.execute(any())).thenReturn(conPerfil(null, null));

        mvc.perform(put("/api/v1/users/me").contentType("application/json").content("""
                        {"displayName":"Ana Maria","city":null,"phone":null}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").doesNotExist())
                .andExpect(jsonPath("$.phone").doesNotExist());
    }

    @Test
    void deberia_rechazar_un_perfil_sin_nombre_criterio_21() throws Exception {
        mvc.perform(put("/api/v1/users/me").contentType("application/json").content("""
                        {"displayName":"  ","city":null,"phone":null}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"));

        verify(perfil, never()).execute(any());
    }

    /**
     * Lo que el dominio rechaza tiene que salir como 400 y no como 500: un
     * telefono con letras es un error de quien escribe, no del servidor.
     */
    @Test
    void deberia_traducir_a_400_lo_que_el_dominio_rechaza_criterio_21() throws Exception {
        when(perfil.execute(any())).thenThrow(new IllegalArgumentException("El telefono no es valido"));

        mvc.perform(put("/api/v1/users/me").contentType("application/json").content("""
                        {"displayName":"Ana Maria","city":null,"phone":"no-es-numero"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"));
    }

    /**
     * Criterio 21: pedir el cambio responde 202 y no cambia nada todavia. El
     * correo nuevo se verifica antes de reemplazar al anterior.
     */
    @Test
    void deberia_aceptar_la_peticion_de_cambio_de_correo_con_202_criterio_21() throws Exception {
        mvc.perform(post("/api/v1/users/me/email")
                        .contentType("application/json")
                        .content("""
                        {"newEmail":"nueva@correo.co"}
                        """))
                .andExpect(status().isAccepted());

        ArgumentCaptor<RequestEmailChangeCommand> comando = ArgumentCaptor.forClass(RequestEmailChangeCommand.class);
        verify(cambioDeCorreo).execute(comando.capture());
        assertThat(comando.getValue().usuario()).isEqualTo(USUARIO);
        assertThat(comando.getValue().newEmail()).isEqualTo("nueva@correo.co");
    }

    @Test
    void deberia_rechazar_un_correo_con_formato_invalido_criterio_21() throws Exception {
        mvc.perform(post("/api/v1/users/me/email")
                        .contentType("application/json")
                        .content("""
                        {"newEmail":"no-es-un-correo"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"));

        verify(cambioDeCorreo, never()).execute(any());
    }
}
