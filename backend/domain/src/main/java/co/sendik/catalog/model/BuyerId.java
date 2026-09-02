package co.sendik.catalog.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Identificador de quien marca un favorito. HU-011.
 *
 * <p><strong>Propio de {@code catalog} y no el {@code UserId} de {@code identity}.</strong>
 * Los tres identificadores de persona de este contexto —{@link SellerId},
 * {@link ModeratorId} y este— envuelven el mismo UUID y son tipos distintos a proposito:
 * un contexto no importa el modelo de otro, que es lo que mantiene abierta la puerta a
 * separarlos sin reescribir el dominio (docs/arquitectura/vision-tecnica.md).
 *
 * <p><strong>Comprador y no «quien guarda», aunque marcar no sea comprar todavia.</strong>
 * El glosario nombra a esta persona {@code Buyer} y el dominio se nombra con el glosario.
 * Lo que el nombre dice es a que rol apunta el gesto, no que haya una transaccion hecha.
 *
 * <p>Que sea un tipo distinto de {@link SellerId} es justo lo que hace visible RN-072: la
 * comparacion entre quien marca y quien vende no puede escribirse por descuido, hay que
 * bajar a los UUID a proposito. Lo hace {@link Favorite}, que es el unico sitio donde esa
 * regla vive.
 */
public record BuyerId(UUID value) {

    public BuyerId {
        Objects.requireNonNull(value, "El identificador es obligatorio");
    }

    public static BuyerId de(String texto) {
        Objects.requireNonNull(texto, "El identificador es obligatorio");
        try {
            return new BuyerId(UUID.fromString(texto));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El identificador no es un UUID valido", e);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
