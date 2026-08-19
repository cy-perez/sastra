package co.sastra.identity.port.out;

import co.sastra.identity.model.UserCredentials;
import co.sastra.identity.model.UserId;
import java.util.Optional;

/**
 * Puerto de salida hacia las credenciales y su contador de intentos (RN-006).
 *
 * <p>Esta separado de {@link UserRepository} por lo mismo que
 * {@link co.sastra.identity.model.UserCredentials} esta separado de
 * {@code User}: cargar un usuario para mostrar su nombre no debe traer consigo
 * con que suplantarlo.
 */
public interface CredentialsRepository {

    Optional<UserCredentials> buscarPorUsuario(UserId usuario);

    void actualizar(UserCredentials credenciales);

    /**
     * Escribe la contrasena nueva, su fecha y el contador ya limpio.
     *
     * <p>Es un metodo aparte de {@link #actualizar} y no un parametro mas, porque
     * son dos operaciones con permisos distintos sobre la misma fila: contar un
     * intento fallido no puede tocar la credencial. Si compartieran metodo, el
     * camino del ingreso reescribiria el hash en cada fallo.
     *
     * <p>El bloqueo de RN-006 se levanta con el cambio: quien llega aqui demostro
     * control del buzon, que es mas fuerte que la contrasena.
     */
    void cambiarContrasena(UserCredentials credenciales);
}
