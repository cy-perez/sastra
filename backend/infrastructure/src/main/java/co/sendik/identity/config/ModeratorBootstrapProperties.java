package co.sendik.identity.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Quien arranca siendo moderador (HU-006).
 *
 * @param moderators correos de cuentas que reciben el rol {@code MODERATOR} al arrancar.
 *     <p><strong>Vacia por omision, y vacia no hace nada.</strong> Es la respuesta a una
 *     pregunta que HU-002 dejo sin responder: como existe el primer moderador en un
 *     entorno nuevo sin que alguien entre a la base de produccion a escribir un
 *     {@code INSERT}.
 *     <p>Es una lista de correos y no de identificadores porque un identificador no se
 *     conoce hasta que la cuenta existe, y quien configura el entorno sabe a quien
 *     quiere dar acceso, no que UUID le toco.
 *     <p>No es una puerta trasera: no crea cuentas, no abre sesiones y no salta ninguna
 *     comprobacion. Concede un rol a una cuenta que ya existe, que es exactamente lo que
 *     hoy se hace a mano.
 */
@Validated
@ConfigurationProperties(prefix = "sendik.security")
public record ModeratorBootstrapProperties(List<String> moderators) {

    public ModeratorBootstrapProperties {
        moderators = moderators == null ? List.of() : List.copyOf(moderators);
    }
}
