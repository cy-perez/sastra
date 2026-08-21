package co.sastra.identity.usecase;

import co.sastra.identity.exception.AccountNoLongerExistsException;
import co.sastra.identity.model.User;
import co.sastra.identity.model.UserId;
import co.sastra.identity.port.out.UserRepository;
import co.sastra.shared.port.out.PublicFileStore;
import org.springframework.transaction.annotation.Transactional;

/**
 * Criterio 21: quitar la foto de perfil.
 *
 * <p>Mismo orden que al ponerla y por el mismo motivo: primero se guarda la cuenta
 * sin foto y despues se borra el archivo. Al reves, un fallo al guardar dejaria la
 * fila apuntando a un archivo borrado.
 *
 * <p>Es idempotente: quitar la foto de quien no tiene foto no es un error, es que
 * ya esta como se pidio.
 */
public class RemoveAvatarUseCase {

    private final UserRepository usuarios;
    private final PublicFileStore almacen;

    public RemoveAvatarUseCase(UserRepository usuarios, PublicFileStore almacen) {
        this.usuarios = usuarios;
        this.almacen = almacen;
    }

    @Transactional
    public User execute(UserId usuario) {
        User cuenta = usuarios.buscarPorId(usuario).orElseThrow(AccountNoLongerExistsException::new);

        User.CambioDeAvatar cambio = cuenta.sinAvatar();

        if (cambio.anterior() == null) {
            return cuenta;
        }

        usuarios.actualizar(cambio.cuenta());
        almacen.borrar(cambio.anterior());

        return cambio.cuenta();
    }
}
