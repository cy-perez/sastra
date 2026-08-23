package co.sastra.identity.config;

import co.sastra.identity.model.Email;
import co.sastra.identity.port.out.ConfiguredModerators;
import org.springframework.stereotype.Component;

/**
 * Responde si un correo esta declarado como moderador en la configuracion (HU-006).
 *
 * <p>Adaptador aparte y no el propio record de enlace, como {@code ConfiguredLegalDocuments}
 * con {@code LegalDocumentProperties}: el record dice que hay configurado y este dice que
 * significa. Mezclarlos deja un tipo que es a la vez el formato del YAML y una regla.
 */
@Component
public class ConfiguredModeratorEmails implements ConfiguredModerators {

    private final ModeratorBootstrapProperties propiedades;

    public ConfiguredModeratorEmails(ModeratorBootstrapProperties propiedades) {
        this.propiedades = propiedades;
    }

    /**
     * Compara objetos de valor, no cadenas.
     *
     * <p>El correo de la variable lo escribio una persona y puede traer mayusculas y
     * espacios; {@link Email} normaliza al construirse (RN-001). Comparar por ahi evita
     * reimplementar esa normalizacion y que las dos se separen. Una entrada que no sea un
     * correo simplemente no coincide con nadie: el arranque ya la cuenta aparte.
     */
    @Override
    public boolean incluye(Email correo) {
        return propiedades.moderators().stream().anyMatch(configurado -> mismoCorreo(configurado, correo));
    }

    private static boolean mismoCorreo(String configurado, Email correo) {
        try {
            return new Email(configurado).equals(correo);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
