package co.sastra.identity.config;

import co.sastra.identity.model.Email;
import co.sastra.identity.port.out.ConfiguredModerators;
import java.util.List;
import java.util.Locale;
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
@ConfigurationProperties(prefix = "sastra.security")
public record ModeratorBootstrapProperties(List<String> moderators) implements ConfiguredModerators {

    public ModeratorBootstrapProperties {
        moderators = moderators == null ? List.of() : List.copyOf(moderators);
    }

    /**
     * Compara ya normalizado por los dos lados.
     *
     * <p>El correo del objeto de valor viene en minusculas y sin espacios (RN-001); el de
     * la variable de entorno lo escribio una persona y puede traer de todo. Sin normalizar
     * aqui, un `Moderadora@Sastra.CO` en la configuracion no coincidiria con la cuenta y
     * esa persona se quedaria sin acceso sin que nada fallara.
     */
    @Override
    public boolean incluye(Email correo) {
        return moderators.stream()
                .anyMatch(configurado -> normalizar(configurado).equals(correo.value()));
    }

    private static String normalizar(String correo) {
        return correo.trim().toLowerCase(Locale.ROOT);
    }
}
