package co.sastra.identity.port.out;

import co.sastra.identity.model.Email;

/**
 * Quien esta declarado como moderador en la configuracion del entorno (HU-006).
 *
 * <p>Un puerto y no la lista de correos suelta porque la capa de aplicacion no lee
 * configuracion: le basta con poder preguntar. Quien responde es
 * {@code ModeratorBootstrapProperties}, en {@code infrastructure}.
 *
 * <p>Se pregunta en dos momentos, y hacen falta los dos:
 *
 * <ul>
 *   <li>Al arrancar, para las cuentas que ya existen.
 *   <li>Al registrarse, para las que todavia no. Sin esto, configurar el correo antes de
 *       que la persona se registre no serviria de nada, y ese es justo el orden natural
 *       de dar de alta a alguien: primero se decide quien va a moderar, despues esa
 *       persona crea su cuenta.
 * </ul>
 */
public interface ConfiguredModerators {

    /** Con la lista vacia, que es lo normal, siempre es {@code false}. */
    boolean incluye(Email correo);
}
