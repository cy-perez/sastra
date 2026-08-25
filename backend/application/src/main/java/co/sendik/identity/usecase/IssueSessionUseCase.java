package co.sendik.identity.usecase;

import co.sendik.identity.dto.AuthenticatedUser;
import co.sendik.identity.dto.IssueSessionCommand;
import co.sendik.identity.dto.SessionResult;
import co.sendik.identity.model.RefreshToken;
import co.sendik.identity.model.User;
import co.sendik.identity.port.out.AccessTokenIssuer;
import co.sendik.identity.port.out.RefreshTokenRepository;
import co.sendik.identity.port.out.TokenGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;

/**
 * Abre una sesion nueva: el unico sitio del sistema que acuna un par de tokens
 * desde cero.
 *
 * <p>Lo usan el ingreso con credenciales y la verificacion del correo, que son las
 * dos formas de empezar una sesion (criterios 9 y 10). El refresco no pasa por
 * aqui porque no abre nada: continua una familia existente rotando su ultimo
 * token, y esa operacion la hace el propio {@link RefreshToken}.
 *
 * <p>Que exista una sola puerta de entrada evita la version peligrosa de este
 * codigo, que es cada caso de uso emitiendo tokens a su manera y uno de ellos
 * olvidando guardar el hash o poniendo otra vigencia.
 */
public class IssueSessionUseCase {

    private final RefreshTokenRepository refrescos;
    private final AccessTokenIssuer accesos;
    private final TokenGenerator generadorDeTokens;

    /**
     * Sale de la configuracion ({@code JWT_REFRESH_TTL}) y no de
     * {@link RefreshToken#VIGENCIA} para poder acortarla en un entorno de pruebas
     * sin recompilar. La constante del dominio sigue siendo la referencia de
     * RN-007 y el valor por omision.
     */
    private final Duration vigenciaDelRefresco;

    private final Clock reloj;

    public IssueSessionUseCase(
            RefreshTokenRepository refrescos,
            AccessTokenIssuer accesos,
            TokenGenerator generadorDeTokens,
            Duration vigenciaDelRefresco,
            Clock reloj) {
        this.refrescos = refrescos;
        this.accesos = accesos;
        this.generadorDeTokens = generadorDeTokens;
        this.vigenciaDelRefresco = vigenciaDelRefresco;
        this.reloj = reloj;
    }

    @Transactional
    public SessionResult execute(IssueSessionCommand comando) {
        Instant ahora = reloj.instant();
        User usuario = comando.usuario();

        TokenGenerator.GeneratedToken refresco = generadorDeTokens.generar();

        RefreshToken token = RefreshToken.abrirSesion(
                usuario.id(), refresco.hash(), ahora, vigenciaDelRefresco, comando.userAgent(), comando.ipHash());

        refrescos.guardar(token);

        AccessTokenIssuer.IssuedAccessToken acceso = accesos.emitir(usuario, token.familyId(), ahora);

        return new SessionResult(
                acceso.value(), acceso.expiresAt(), refresco.valorEnClaro(), token.expiresAt(), resumirA(usuario));
    }

    /** Compartido con el refresco, que devuelve el mismo resumen del usuario. */
    static AuthenticatedUser resumirA(User usuario) {
        return new AuthenticatedUser(
                usuario.id(),
                usuario.email().value(),
                usuario.displayName().value(),
                usuario.tieneElCorreoVerificado(),
                usuario.roles());
    }
}
