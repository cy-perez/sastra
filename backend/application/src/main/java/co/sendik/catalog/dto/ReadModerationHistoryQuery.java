package co.sendik.catalog.dto;

import co.sendik.catalog.model.ListingId;
import co.sendik.catalog.model.SellerId;

/**
 * El rastro de una publicacion propia. HU-013.
 *
 * <p>Lleva el vendedor porque el rastro es de quien pregunta y de nadie mas: el criterio 7
 * exige 404 —y nunca 403— sobre una publicacion ajena, y para eso hace falta saber de quien
 * es. Sale del contexto de seguridad, jamas de un parametro de la peticion.
 */
public record ReadModerationHistoryQuery(SellerId vendedor, ListingId publicacion) {

    public ReadModerationHistoryQuery {
        if (vendedor == null) {
            throw new IllegalArgumentException("El vendedor es obligatorio");
        }
        if (publicacion == null) {
            throw new IllegalArgumentException("La publicacion es obligatoria");
        }
    }
}
