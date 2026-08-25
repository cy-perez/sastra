package co.sendik.identity.dto;

import co.sendik.identity.model.Role;
import co.sendik.identity.model.UserId;
import java.util.Set;

/**
 * Lo minimo que la interfaz necesita saber de quien acaba de entrar.
 *
 * <p>No es el agregado {@code User} recortado por comodidad: es lo que el cliente
 * usa para pintar la cabecera y decidir que enseña. La fecha de nacimiento, la
 * ciudad o el telefono no estan porque nadie los necesita para eso, y un dato que
 * no viaja no se puede filtrar (docs/operacion/datos-personales.md).
 *
 * @param emailVerified criterio 13: una cuenta sin verificar entra, pero solo ve
 *     el aviso de verificacion pendiente. El cliente lo decide con este campo
 */
public record AuthenticatedUser(UserId id, String email, String displayName, boolean emailVerified, Set<Role> roles) {

    /**
     * Sin el correo ni el nombre.
     *
     * <p>El {@code toString} de un record los imprimiria, y este objeto viaja por
     * todos los casos de uso de sesion: cualquier registro que lo incluya deja
     * datos personales en el registro del servidor
     * (docs/operacion/datos-personales.md). El identificador si va, que es
     * justamente lo que sirve para investigar sin identificar a nadie.
     */
    @Override
    public String toString() {
        return "AuthenticatedUser[" + id + ", verificado=" + emailVerified + ", roles=" + roles + "]";
    }
}
