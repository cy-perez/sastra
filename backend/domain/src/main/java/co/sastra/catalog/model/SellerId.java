package co.sastra.catalog.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Identificador de el vendedor dueno de la publicacion.
 *
 * <p><strong>Propio de {@code catalog} y no el {@code UserId} de {@code identity}.</strong>
 * Los dos envuelven el mismo UUID, y aun asi se declara aqui: un contexto no importa el
 * modelo de otro, que es lo que mantiene abierta la puerta a separarlos sin reescribir el
 * dominio (docs/arquitectura/vision-tecnica.md). El nombre tambien dice mas: aqui esta
 * persona no es una cuenta, es quien vende o quien modera.
 */
public record SellerId(UUID value) {

    public SellerId {
        Objects.requireNonNull(value, "El identificador es obligatorio");
    }

    public static SellerId de(String texto) {
        Objects.requireNonNull(texto, "El identificador es obligatorio");
        try {
            return new SellerId(UUID.fromString(texto));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El identificador no es un UUID valido", e);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
