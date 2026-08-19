package co.sastra.identity.port.out;

import co.sastra.identity.model.RefreshToken;
import co.sastra.identity.model.RefreshTokenId;
import co.sastra.identity.model.TokenFamilyId;
import co.sastra.identity.model.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Puerto de salida hacia el almacen de tokens de refresco. */
public interface RefreshTokenRepository {

    void guardar(RefreshToken token);

    /**
     * Consume un token y emite su reemplazo, <strong>en una sola operacion</strong>.
     *
     * <p>Los dos movimientos de una rotacion no pueden quedar a medias. Si se
     * guardara el nuevo sin marcar el anterior, la familia tendria dos tokens vivos y
     * el siguiente uso del viejo pasaria por una rotacion normal en vez de por la
     * deteccion de reutilizacion: se perderia justo el criterio 15.
     *
     * <p>Que la atomicidad sea responsabilidad del adaptador y no de una transaccion
     * alrededor del caso de uso es deliberado. Este caso de uso tambien escribe
     * revocaciones que deben sobrevivir a una excepcion, y las dos cosas no caben en
     * la misma transaccion.
     */
    void rotar(RefreshToken consumido, RefreshToken emitido);

    /**
     * Se busca por hash y nunca por el valor: el token que llega en la cookie se
     * hashea antes de consultar, asi que la base nunca ve el original.
     */
    Optional<RefreshToken> buscarPorHash(String tokenHash);

    /**
     * Busca por identificador, no por hash.
     *
     * <p>Existe para una sola pregunta: dado un token ya consumido, saber si el que
     * lo reemplazo sigue sin usarse. Es la mitad no temporal de la ventana de
     * gracia de RN-007, y necesita llegar al reemplazo por su {@code replaced_by},
     * que es un identificador y no un hash. Nadie puede pedir un token por
     * identificador desde fuera: el identificador no viaja a ningun cliente.
     */
    Optional<RefreshToken> buscarPorId(RefreshTokenId id);

    /**
     * Revoca de una vez todos los tokens vivos de una familia. Es la reaccion al
     * criterio 15 y tambien la forma de cerrar sesion del criterio 16.
     *
     * <p>Va en una sola operacion y no leyendo la familia para revocarla token a
     * token: entre la lectura y la escritura cabe una rotacion, y el token que
     * naciera en ese hueco quedaria vivo justo cuando se esta cortando la sesion.
     *
     * @return cuantos tokens se revocaron
     */
    int revocarFamilia(TokenFamilyId familia, Instant ahora);

    /**
     * Revoca todas las sesiones vivas de una persona, en todos sus dispositivos.
     *
     * <p>Es el criterio 20: al cambiar la contrasena se cierra todo. Si la contrasena
     * se cambio porque alguien la habia averiguado, dejar viva la sesion que ese
     * alguien ya tenia abierta haria inutil el cambio: el token de refresco dura 30
     * dias y no depende de la contrasena.
     *
     * @return cuantas sesiones se cortaron
     */
    int revocarTodasDe(UserId usuario, Instant ahora);

    /**
     * Las sesiones vivas de una persona, una por familia. Criterio 17.
     *
     * <p>Devuelve la cabeza viva de cada familia, que es el token que hoy sirve. Una
     * familia son todas las rotaciones de la misma sesion, asi que listar tokens en
     * vez de cabezas mostraria la misma sesion repetida tantas veces como se haya
     * refrescado, que en 30 dias son muchas.
     */
    List<RefreshToken> listarSesionesActivasDe(UserId usuario, Instant ahora);

    /**
     * Cierra una sesion concreta de una persona concreta. Criterio 17.
     *
     * <p><strong>El identificador del usuario no sobra.</strong> Sin el, quien
     * conociera el identificador de una familia ajena podria cerrar la sesion de
     * otra persona: la comprobacion va en el {@code WHERE} y no en un {@code if}
     * previo, para que no haya hueco entre comprobar y escribir.
     *
     * @return cuantos tokens se revocaron. Cero significa que esa sesion no existe
     *     o no es suya, y las dos cosas se responden igual
     */
    int revocarSesionDe(UserId usuario, TokenFamilyId familia, Instant ahora);
}
