package co.sastra.catalog.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Identificador de el moderador que decide sobre una publicacion.
 *
 * <p><strong>Propio de {@code catalog} y no el {@code UserId} de {@code identity}.</strong>
 * Los dos envuelven el mismo UUID, y aun asi se declara aqui: un contexto no importa el
 * modelo de otro, que es lo que mantiene abierta la puerta a separarlos sin reescribir el
 * dominio (docs/arquitectura/vision-tecnica.md). El nombre tambien dice mas: aqui esta
 * persona no es una cuenta, es quien vende o quien modera.
 */
public record ModeratorId(UUID value) {

    public ModeratorId {
        Objects.requireNonNull(value, "El identificador es obligatorio");
    }

    public static ModeratorId de(String texto) {
        Objects.requireNonNull(texto, "El identificador es obligatorio");
        try {
            return new ModeratorId(UUID.fromString(texto));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El identificador no es un UUID valido", e);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
