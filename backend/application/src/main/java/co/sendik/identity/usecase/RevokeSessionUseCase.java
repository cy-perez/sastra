package co.sendik.identity.usecase;

import co.sendik.identity.model.TokenFamilyId;
import co.sendik.identity.model.UserId;
import co.sendik.identity.port.out.RefreshTokenRepository;
import java.time.Clock;

/**
 * Cierra una sesion concreta desde la lista. Criterio 17.
 *
 * <p><strong>No lanza si la sesion no existe.</strong> Es deliberado: una sesion
 * ajena y una sesion que ya se cerro se responden igual, y asi nadie puede
 * averiguar si un identificador de familia pertenece a alguien probandolo. El
 * resultado observable es el mismo que la persona queria, que esa sesion no siga
 * abierta.
 *
 * <p>Cerrar la propia esta permitido y es util: es como se cierra la sesion desde
 * un dispositivo que ya no se tiene a mano. El borde avisa cual es cual para que
 * la persona sepa lo que hace.
 */
public class RevokeSessionUseCase {

    private final RefreshTokenRepository refrescos;
    private final Clock reloj;

    public RevokeSessionUseCase(RefreshTokenRepository refrescos, Clock reloj) {
        this.refrescos = refrescos;
        this.reloj = reloj;
    }

    public void execute(UserId usuario, TokenFamilyId sesion) {
        // La pertenencia se comprueba en el WHERE del propio UPDATE: entre
        // comprobar y escribir no cabe nada.
        refrescos.revocarSesionDe(usuario, sesion, reloj.instant());
    }
}
