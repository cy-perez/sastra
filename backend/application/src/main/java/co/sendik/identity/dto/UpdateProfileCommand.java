package co.sendik.identity.dto;

import co.sendik.identity.model.UserId;
import org.jspecify.annotations.Nullable;

/**
 * Edicion del perfil. Criterio 21.
 *
 * <p>El correo no esta aqui: cambiarlo exige verificar el nuevo antes de
 * reemplazar el anterior, asi que es otra operacion con otro ritmo.
 *
 * @param usuario sale del token, nunca de la peticion
 * @param city nulo para quitarla. La ausencia y la cadena vacia significan lo
 *     mismo, y el borde ya las ha unificado antes de llegar aqui
 */
public record UpdateProfileCommand(
        UserId usuario,
        String displayName,
        @Nullable String city,
        @Nullable String phone) {}
