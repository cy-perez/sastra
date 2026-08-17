package co.sastra.identity.rest;

import co.sastra.identity.dto.LoginCommand;
import co.sastra.identity.dto.LogoutCommand;
import co.sastra.identity.dto.RefreshSessionCommand;
import co.sastra.identity.dto.RegisterUserCommand;
import co.sastra.identity.dto.ResendVerificationCommand;
import co.sastra.identity.dto.SessionResult;
import co.sastra.identity.dto.VerifyEmailCommand;
import co.sastra.identity.dto.VerifyEmailResult;
import co.sastra.identity.rest.dto.LoginRequest;
import co.sastra.identity.rest.dto.RegisterRequest;
import co.sastra.identity.rest.dto.ResendVerificationRequest;
import co.sastra.identity.rest.dto.SessionResponse;
import co.sastra.identity.rest.dto.VerifyEmailRequest;
import co.sastra.identity.rest.dto.VerifyEmailResponse;
import co.sastra.identity.rest.mapper.SessionResponses;
import co.sastra.identity.usecase.LoginUseCase;
import co.sastra.identity.usecase.LogoutUseCase;
import co.sastra.identity.usecase.RefreshSessionUseCase;
import co.sastra.identity.usecase.RegisterUserUseCase;
import co.sastra.identity.usecase.ResendVerificationUseCase;
import co.sastra.identity.usecase.VerifyEmailUseCase;
import co.sastra.shared.rest.ClientIpHasher;
import co.sastra.shared.rest.RefreshCookies;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registro, verificacion y sesion. HU-001 rebanadas A y B.
 *
 * <p>Este controlador delega y traduce, nada mas: si tuviera un {@code if} de
 * negocio estaria en el sitio equivocado (backend/CLAUDE.md).
 *
 * <p>Las tres rutas de sesion son publicas porque quien las usa todavia no tiene
 * token de acceso. La credencial que presentan es otra: la contrasena en el ingreso
 * y la cookie de refresco en las otras dos.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    /**
     * El {@code User-Agent} se guarda para que la persona reconozca sus sesiones, no
     * para analizarlo. Se recorta porque es texto que manda el cliente y no tiene por
     * que ser razonable.
     */
    private static final int LARGO_MAXIMO_DEL_AGENTE = 255;

    private final RegisterUserUseCase casoDeRegistro;
    private final VerifyEmailUseCase casoDeVerificacion;
    private final ResendVerificationUseCase casoDeReenvio;
    private final LoginUseCase casoDeIngreso;
    private final RefreshSessionUseCase casoDeRefresco;
    private final LogoutUseCase casoDeCierre;
    private final SessionResponses respuestas;
    private final RefreshCookies cookies;
    private final ClientIpHasher hasherDeIp;

    public AuthController(
            RegisterUserUseCase casoDeRegistro,
            VerifyEmailUseCase casoDeVerificacion,
            ResendVerificationUseCase casoDeReenvio,
            LoginUseCase casoDeIngreso,
            RefreshSessionUseCase casoDeRefresco,
            LogoutUseCase casoDeCierre,
            SessionResponses respuestas,
            RefreshCookies cookies,
            ClientIpHasher hasherDeIp) {
        this.casoDeRegistro = casoDeRegistro;
        this.casoDeVerificacion = casoDeVerificacion;
        this.casoDeReenvio = casoDeReenvio;
        this.casoDeIngreso = casoDeIngreso;
        this.casoDeRefresco = casoDeRefresco;
        this.casoDeCierre = casoDeCierre;
        this.respuestas = respuestas;
        this.cookies = cookies;
        this.hasherDeIp = hasherDeIp;
    }

    /**
     * Devuelve <strong>202 sin cuerpo</strong>, y no 201 con {@code Location}.
     *
     * <p>Es lo que hace indistinguibles los dos caminos del criterio 2: una
     * cabecera {@code Location} apuntando al recurso creado diria que el correo
     * no existia, y su ausencia diria lo contrario. Con 202 vacio, registrar un
     * correo nuevo y registrar uno que ya tiene cuenta se responden igual.
     */
    @PostMapping("/register")
    public ResponseEntity<Void> registrar(@Valid @RequestBody RegisterRequest peticion, HttpServletRequest http) {
        casoDeRegistro.execute(new RegisterUserCommand(
                peticion.email(),
                peticion.password(),
                peticion.displayName(),
                peticion.birthDate(),
                peticion.locale(),
                peticion.acceptsTerms(),
                peticion.acceptsPrivacy(),
                hasherDeIp.hashear(http)));

        return ResponseEntity.accepted().build();
    }

    /** Criterio 9: verificar el correo deja la cuenta activa y la sesion abierta. */
    @PostMapping("/verify-email")
    public ResponseEntity<VerifyEmailResponse> verificar(
            @Valid @RequestBody VerifyEmailRequest peticion, HttpServletRequest http) {
        VerifyEmailResult resultado = casoDeVerificacion.execute(
                new VerifyEmailCommand(peticion.token(), agenteDe(http), hasherDeIp.hashear(http)));

        SessionResponse sesion = respuestas.de(resultado.session());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieDe(resultado.session()))
                .body(new VerifyEmailResponse(sesion, resultado.yaEstabaVerificado()));
    }

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void reenviar(@Valid @RequestBody ResendVerificationRequest peticion) {
        casoDeReenvio.execute(new ResendVerificationCommand(peticion.expiredToken()));
    }

    /** Criterio 10: token de acceso en el cuerpo y token de refresco en la cookie. */
    @PostMapping("/login")
    public ResponseEntity<SessionResponse> entrar(@Valid @RequestBody LoginRequest peticion, HttpServletRequest http) {
        SessionResult sesion = casoDeIngreso.execute(
                new LoginCommand(peticion.email(), peticion.password(), agenteDe(http), hasherDeIp.hashear(http)));

        return conSesion(sesion);
    }

    /**
     * Criterio 14: no recibe cuerpo. La credencial es la cookie, y pedirla tambien en
     * el cuerpo permitiria refrescar con un token copiado a mano, que es justo lo que
     * la cookie {@code HttpOnly} evita.
     */
    @PostMapping("/refresh")
    public ResponseEntity<SessionResponse> refrescar(HttpServletRequest http) {
        SessionResult sesion = casoDeRefresco.execute(
                new RefreshSessionCommand(cookies.leerDe(http).orElse(null), agenteDe(http), hasherDeIp.hashear(http)));

        return conSesion(sesion);
    }

    /**
     * Criterio 16: revoca en el servidor y borra la cookie.
     *
     * <p>Responde 204 siempre, tambien sin cookie o con una que ya no sirve: quien
     * quiere salir termina fuera, y un error aqui solo dejaria al cliente sin saber
     * si limpiar su estado.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> salir(HttpServletRequest http) {
        casoDeCierre.execute(new LogoutCommand(cookies.leerDe(http).orElse(null)));

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.vacia().toString())
                .build();
    }

    private ResponseEntity<SessionResponse> conSesion(SessionResult sesion) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieDe(sesion))
                .body(respuestas.de(sesion));
    }

    private String cookieDe(SessionResult sesion) {
        return cookies.con(sesion.refreshToken()).toString();
    }

    private static @Nullable String agenteDe(HttpServletRequest http) {
        String agente = http.getHeader(HttpHeaders.USER_AGENT);
        if (agente == null || agente.isBlank()) {
            return null;
        }
        return agente.length() > LARGO_MAXIMO_DEL_AGENTE ? agente.substring(0, LARGO_MAXIMO_DEL_AGENTE) : agente;
    }
}
