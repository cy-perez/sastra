package co.sastra.identity.rest;

import co.sastra.identity.dto.RegisterUserCommand;
import co.sastra.identity.dto.ResendVerificationCommand;
import co.sastra.identity.dto.VerifyEmailCommand;
import co.sastra.identity.dto.VerifyEmailResult;
import co.sastra.identity.rest.dto.RegisterRequest;
import co.sastra.identity.rest.dto.ResendVerificationRequest;
import co.sastra.identity.rest.dto.VerifyEmailRequest;
import co.sastra.identity.rest.dto.VerifyEmailResponse;
import co.sastra.identity.usecase.RegisterUserUseCase;
import co.sastra.identity.usecase.ResendVerificationUseCase;
import co.sastra.identity.usecase.VerifyEmailUseCase;
import co.sastra.shared.rest.ClientIpHasher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registro y verificacion de correo. HU-001 rebanada A.
 *
 * <p>Este controlador delega y traduce, nada mas: si tuviera un {@code if} de
 * negocio estaria en el sitio equivocado (backend/CLAUDE.md).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegisterUserUseCase casoDeRegistro;
    private final VerifyEmailUseCase casoDeVerificacion;
    private final ResendVerificationUseCase casoDeReenvio;
    private final ClientIpHasher hasherDeIp;

    public AuthController(
            RegisterUserUseCase casoDeRegistro,
            VerifyEmailUseCase casoDeVerificacion,
            ResendVerificationUseCase casoDeReenvio,
            ClientIpHasher hasherDeIp) {
        this.casoDeRegistro = casoDeRegistro;
        this.casoDeVerificacion = casoDeVerificacion;
        this.casoDeReenvio = casoDeReenvio;
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

    @PostMapping("/verify-email")
    public VerifyEmailResponse verificar(@Valid @RequestBody VerifyEmailRequest peticion) {
        VerifyEmailResult resultado = casoDeVerificacion.execute(new VerifyEmailCommand(peticion.token()));

        return new VerifyEmailResponse(resultado.email(), resultado.yaEstabaVerificado());
    }

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void reenviar(@Valid @RequestBody ResendVerificationRequest peticion) {
        casoDeReenvio.execute(new ResendVerificationCommand(peticion.expiredToken()));
    }
}
