package co.sastra.identity.rest;

import co.sastra.identity.dto.RequestEmailVerificationCommand;
import co.sastra.identity.model.UserId;
import co.sastra.identity.usecase.RequestEmailVerificationUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lo que una persona puede hacer sobre su propia cuenta ya autenticada.
 *
 * <p>Por ahora solo pedir otro enlace de verificacion, que es lo que necesita el
 * criterio 13: quien entra sin verificar solo ve ese aviso y ese boton. El resto de
 * {@code /users/me} (ver y editar el perfil, descargar los datos, cerrar la cuenta)
 * es de la rebanada siguiente.
 *
 * <p><strong>El identificador sale del token, nunca de la peticion.</strong> Es la
 * diferencia entre un endpoint que actua sobre quien llama y uno que actua sobre
 * quien le digan (backend/CLAUDE.md).
 */
@RestController
@RequestMapping("/api/v1/users/me")
public class UsersController {

    private final RequestEmailVerificationUseCase casoDeReenvio;

    public UsersController(RequestEmailVerificationUseCase casoDeReenvio) {
        this.casoDeReenvio = casoDeReenvio;
    }

    /**
     * Criterio 13. Comparte con el reenvio publico el limite de tres por hora, asi
     * que puede responder 429.
     */
    @PostMapping("/email-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void pedirVerificacionDeCorreo(@AuthenticationPrincipal Jwt token) {
        casoDeReenvio.execute(new RequestEmailVerificationCommand(UserId.de(token.getSubject())));
    }
}
