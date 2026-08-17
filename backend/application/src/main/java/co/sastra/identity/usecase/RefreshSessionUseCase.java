package co.sastra.identity.usecase;

import co.sastra.identity.dto.RefreshSessionCommand;
import co.sastra.identity.dto.SessionResult;
import co.sastra.identity.exception.RefreshTokenInvalidException;
import co.sastra.identity.model.RefreshToken;
import co.sastra.identity.model.RefreshTokenId;
import co.sastra.identity.model.User;
import co.sastra.identity.port.out.AccessTokenIssuer;
import co.sastra.identity.port.out.MailSender;
import co.sastra.identity.port.out.RefreshTokenRepository;
import co.sastra.identity.port.out.TokenGenerator;
import co.sastra.identity.port.out.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Renovacion de la sesion con rotacion del token de refresco. Criterios 14 y 15.
 *
 * <p>El criterio 14 es la mitad facil: cada uso emite uno nuevo y consume el
 * anterior, y de eso se encarga {@link RefreshToken#rotar} para que no exista la
 * version a medias.
 *
 * <p>El criterio 15 es la que importa. Si llega un token que ya se habia usado, hay
 * dos ejemplares del mismo secreto en circulacion y no se puede saber cual es el
 * legitimo: el ladron podria ser el que acaba de llamar o el que llamo antes. Por
 * eso no se elige, se cierra la sesion completa y se avisa. Es molesto para el
 * titular una vez y caro para el atacante siempre.
 *
 * <p><strong>Con una excepcion, la ventana de gracia de RN-007.</strong> La cookie
 * es del navegador entero, no de una pestana: dos pestanas que arrancan a la vez
 * mandan el mismo token de refresco, la primera lo rota y la segunda llega con uno
 * recien consumido. Sin la ventana, abrir dos pestanas cerraba la sesion y mandaba
 * al titular un correo diciendo que le habian copiado la sesion. El aviso mas
 * importante del sistema se disparaba solo, y un aviso que se dispara solo deja de
 * leerse justo para el dia en que sea verdad.
 *
 * <p>La gracia exige las dos condiciones a la vez: que la rotacion sea de hace muy
 * poco y que el token que salio de ella siga sin usarse. Lo segundo es lo que la
 * mantiene estrecha, porque en cuanto la cadena avanza un paso mas ya hay alguien
 * usando la sesion y cualquier token viejo que aparezca vuelve a ser un incidente.
 * Dentro de la ventana no se emite nada: se rechaza igual, sin revocar, y el cliente
 * reintenta con la cookie que el navegador ya tiene, que es la buena.
 *
 * <p><strong>Este caso de uso no abre transaccion, y es a proposito.</strong> Su
 * escritura mas importante ocurre justo antes de lanzar una excepcion: la
 * revocacion de la familia. Dentro de una transaccion, Spring la revertiria al ver
 * la {@code RuntimeException} y el criterio 15 quedaria escrito y sin efecto. Lo
 * unico que si necesita ser atomico es la rotacion, y de eso responde
 * {@link RefreshTokenRepository#rotar}, que es una sola operacion.
 */
public class RefreshSessionUseCase {

    private final RefreshTokenRepository refrescos;
    private final UserRepository usuarios;
    private final AccessTokenIssuer accesos;
    private final TokenGenerator generadorDeTokens;
    private final MailSender correo;
    private final Duration vigenciaDelRefresco;
    private final Duration ventanaDeGracia;
    private final Clock reloj;

    public RefreshSessionUseCase(
            RefreshTokenRepository refrescos,
            UserRepository usuarios,
            AccessTokenIssuer accesos,
            TokenGenerator generadorDeTokens,
            MailSender correo,
            Duration vigenciaDelRefresco,
            Duration ventanaDeGracia,
            Clock reloj) {
        this.refrescos = refrescos;
        this.usuarios = usuarios;
        this.accesos = accesos;
        this.generadorDeTokens = generadorDeTokens;
        this.correo = correo;
        this.vigenciaDelRefresco = vigenciaDelRefresco;
        this.ventanaDeGracia = ventanaDeGracia;
        this.reloj = reloj;
    }

    public SessionResult execute(RefreshSessionCommand comando) {
        Instant ahora = reloj.instant();

        // Sin cookie no hay sesion que renovar. Es el caso borde de HU-001 del
        // navegador que la bloquea, y se responde igual que con una invalida: el
        // cliente tiene que volver a pedir las credenciales.
        String recibido = comando.refreshToken();
        if (recibido == null || recibido.isBlank()) {
            throw new RefreshTokenInvalidException();
        }

        String hash = generadorDeTokens.hashearRecibido(recibido);
        RefreshToken presentado = refrescos.buscarPorHash(hash).orElseThrow(RefreshTokenInvalidException::new);

        if (presentado.fueReemplazado()) {
            if (!esUnaCarreraDelPropioCliente(presentado, ahora)) {
                revocarLaFamiliaYAvisar(presentado, ahora);
            }
            throw new RefreshTokenInvalidException();
        }

        // Caducado o revocado: se rechaza sin mas. No es un incidente, es una
        // sesion que se termino, y revocar la familia de alguien que simplemente
        // volvio a los 40 dias solo anadiria ruido.
        TokenGenerator.GeneratedToken siguiente = generadorDeTokens.generar();
        RefreshToken.Rotacion rotacion =
                presentado.rotar(siguiente.hash(), ahora, vigenciaDelRefresco, comando.userAgent(), comando.ipHash());

        // El usuario se recarga en cada refresco a proposito: si verifico su correo
        // o gano el rol de vendedor desde que entro, el token de acceso nuevo debe
        // reflejarlo. Un token de 15 minutos que arrastrara datos de hace 30 dias
        // no serviria de nada.
        User usuario = usuarios.buscarPorId(presentado.userId()).orElseThrow(RefreshTokenInvalidException::new);

        refrescos.rotar(rotacion.consumido(), rotacion.emitido());

        AccessTokenIssuer.IssuedAccessToken acceso = accesos.emitir(usuario, ahora);

        return new SessionResult(
                acceso.value(),
                acceso.expiresAt(),
                siguiente.valorEnClaro(),
                rotacion.emitido().expiresAt(),
                IssueSessionUseCase.resumirA(usuario));
    }

    /**
     * La ventana de gracia de RN-007: distingue una carrera del propio cliente de
     * una reutilizacion de verdad.
     *
     * <p>Las dos condiciones van juntas y ninguna sobra. Solo con el tiempo, un
     * ladron que reprodujera el token en el mismo segundo entraria gratis. Solo con
     * el estado del reemplazo, una sesion abandonada a medias dejaria la puerta
     * abierta indefinidamente: el reemplazo se quedaria sin usar para siempre y
     * cualquier token viejo pasaria por carrera un mes despues.
     *
     * <p>Que el reemplazo siga siendo utilizable significa que nadie ha seguido la
     * cadena: es la firma de dos peticiones que salieron a la vez, no la de alguien
     * que ya esta dentro con la sesion.
     */
    private boolean esUnaCarreraDelPropioCliente(RefreshToken presentado, Instant ahora) {
        if (!presentado.seConsumioDentroDe(ventanaDeGracia, ahora)) {
            return false;
        }

        RefreshTokenId reemplazo = presentado.replacedBy();
        if (reemplazo == null) {
            return false;
        }

        return refrescos
                .buscarPorId(reemplazo)
                .filter(siguiente -> siguiente.esUtilizable(ahora))
                .isPresent();
    }

    /**
     * Criterio 15: se revoca la cadena completa y se avisa al titular.
     *
     * <p>El aviso es lo unico que se hace con condicion: si el usuario ya no existe
     * no hay a quien escribir, y eso no debe impedir la revocacion.
     */
    private void revocarLaFamiliaYAvisar(RefreshToken reutilizado, Instant ahora) {
        refrescos.revocarFamilia(reutilizado.familyId(), ahora);

        Optional<User> titular = usuarios.buscarPorId(reutilizado.userId());
        titular.ifPresent(correo::enviarAvisoDeSesionRevocadaPorSeguridad);
    }
}
