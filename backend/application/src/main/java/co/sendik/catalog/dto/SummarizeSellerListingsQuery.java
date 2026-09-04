package co.sendik.catalog.dto;

import co.sendik.catalog.model.SellerId;

/**
 * Las cifras del panel de un vendedor. HU-012.
 *
 * <p>Solo lleva el vendedor: no se pagina, no se filtra y no se ordena. Es un record de un
 * campo a proposito, y no un {@code SellerId} suelto en la firma del caso de uso, para que
 * anadirle algo despues -un rango de fechas, por ejemplo- no cambie esa firma.
 */
public record SummarizeSellerListingsQuery(SellerId vendedor) {

    public SummarizeSellerListingsQuery {
        if (vendedor == null) {
            throw new IllegalArgumentException("El vendedor es obligatorio");
        }
    }
}
