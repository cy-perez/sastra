package co.sastra.identity.model;

import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Prueba de que una persona acepto un documento legal concreto.
 *
 * <p>Una fila por documento, no una por registro: la Ley 1581 de 2012 exige
 * aceptacion expresa y separada de los terminos y de la politica de tratamiento
 * de datos (docs/operacion/datos-personales.md).
 *
 * <p>La version es obligatoria porque sin ella el consentimiento no se puede
 * demostrar: dentro de dos anos nadie sabra a que texto dijo que si.
 *
 * <p>La IP se guarda como hash y no en claro. Sirve igual como evidencia de que
 * hubo una aceptacion desde algun sitio, y deja de ser un dato de localizacion
 * conservado sin necesidad.
 */
public record Consent(
        ConsentId id,
        UserId userId,
        ConsentDocument document,
        String version,
        Instant acceptedAt,
        @Nullable String ipHash) {

    public Consent {
        Objects.requireNonNull(id, "El identificador es obligatorio");
        Objects.requireNonNull(userId, "El usuario es obligatorio");
        Objects.requireNonNull(document, "El documento es obligatorio");
        Objects.requireNonNull(version, "La version del documento es obligatoria");
        Objects.requireNonNull(acceptedAt, "La fecha de aceptacion es obligatoria");

        version = version.trim();
        if (version.isEmpty()) {
            throw new IllegalArgumentException(
                    "La version del documento no puede estar vacia: sin ella el consentimiento no se puede demostrar");
        }
    }

    public static Consent otorgar(
            UserId userId, ConsentDocument document, String version, Instant ahora, @Nullable String ipHash) {
        return new Consent(ConsentId.nuevo(), userId, document, version, ahora, ipHash);
    }
}
