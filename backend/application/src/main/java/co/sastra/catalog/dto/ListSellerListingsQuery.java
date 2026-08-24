package co.sastra.catalog.dto;

import co.sastra.catalog.model.SellerId;

/** Las publicaciones del vendedor, paginadas. */
public record ListSellerListingsQuery(SellerId vendedor, int pagina, int tamano) {

    private static final int TAMANO_MAXIMO = 50;

    public ListSellerListingsQuery {
        if (pagina < 0) {
            throw new IllegalArgumentException("La pagina no puede ser negativa: " + pagina);
        }
        if (tamano <= 0 || tamano > TAMANO_MAXIMO) {
            throw new IllegalArgumentException("El tamano de pagina va de 1 a " + TAMANO_MAXIMO + ": " + tamano);
        }
    }
}
