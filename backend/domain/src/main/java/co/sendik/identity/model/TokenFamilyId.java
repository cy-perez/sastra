package co.sendik.identity.model;

import co.sendik.shared.id.Uuid7;
import java.util.Objects;
import java.util.UUID;

/**
 * Identificador de una familia de tokens de refresco.
 *
 * <p>Una familia es una sesion: el token que se emite al entrar y todos los que
 * salen de rotarlo comparten este identificador. Es lo que permite cumplir el
 * criterio 15 de HU-001, revocar la cadena completa cuando aparece un token ya
 * consumido, sin tocar las demas sesiones de la misma persona
 * (docs/arquitectura/modelo-datos.md).
 */
public record TokenFamilyId(UUID value) {

    public TokenFamilyId {
        Objects.requireNonNull(value, "El identificador de la familia es obligatorio");
    }

    public static TokenFamilyId nueva() {
        return new TokenFamilyId(Uuid7.nuevo());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
