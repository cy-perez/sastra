package co.sendik.catalog.dto;

import co.sendik.catalog.model.ListingStatus;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Cuantas publicaciones hay en cada estado. HU-012.
 *
 * <p><strong>Estan los siete de RN-061, siempre.</strong> Los que no tienen ninguna valen
 * cero, y eso no es relleno: la historia pide que el cero se diga, porque omitir «0 en
 * revision» obliga a deducir por ausencia, y quien deduce por ausencia no distingue «no
 * tengo ninguna» de «esto no se cargo».
 *
 * <p>El mapa se copia a un {@link EnumMap}, asi que recorrerlo da el orden de la
 * enumeracion. Ese orden es el del ciclo de vida de una publicacion y es el que la pantalla
 * pinta; dejarlo indefinido moveria las cifras de sitio entre dos cargas.
 *
 * <p><strong>No sirve {@code Map.copyOf}</strong> para esto, aunque sea lo natural para
 * copiar y congelar: devuelve un mapa sin orden definido, y con el la promesa del parrafo
 * anterior no se cumple.
 */
public record SellerListingsSummary(Map<ListingStatus, Long> porEstado) {

    public SellerListingsSummary {
        if (porEstado == null) {
            throw new IllegalArgumentException("El resumen necesita el conteo por estado");
        }
        if (porEstado.size() != ListingStatus.values().length) {
            throw new IllegalArgumentException(
                    "El resumen lleva los " + ListingStatus.values().length + " estados: " + porEstado.keySet());
        }
        porEstado = Collections.unmodifiableMap(new EnumMap<>(porEstado));
    }

    /** Cuantas hay en ese estado. Nunca nulo: los siete estan. */
    public long en(ListingStatus estado) {
        return porEstado.get(estado);
    }
}
