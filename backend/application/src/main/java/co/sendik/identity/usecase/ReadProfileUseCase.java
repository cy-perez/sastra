package co.sendik.identity.usecase;

import co.sendik.identity.exception.AccountNoLongerExistsException;
import co.sendik.identity.model.User;
import co.sendik.identity.model.UserId;
import co.sendik.identity.port.out.UserRepository;

/**
 * El perfil de quien pregunta. Criterio 21.
 *
 * <p>Existe como caso de uso propio y no como una lectura suelta en el
 * controlador para que la regla que importa quede en un solo sitio: el
 * identificador sale del token, asi que nadie puede leer el perfil de otra
 * persona.
 */
public class ReadProfileUseCase {

    private final UserRepository usuarios;

    public ReadProfileUseCase(UserRepository usuarios) {
        this.usuarios = usuarios;
    }

    public User execute(UserId usuario) {
        return usuarios.buscarPorId(usuario).orElseThrow(AccountNoLongerExistsException::new);
    }
}
