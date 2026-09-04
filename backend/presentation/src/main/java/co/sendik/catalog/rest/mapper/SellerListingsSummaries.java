package co.sendik.catalog.rest.mapper;

import co.sendik.catalog.dto.SellerListingsSummary;
import co.sendik.catalog.rest.dto.SellerListingsSummaryResponse;
import co.sendik.catalog.rest.dto.SellerListingsSummaryResponse.StatusCount;

/**
 * Del resumen de la aplicacion al de la API. HU-012.
 *
 * <p>Vive aqui y no como factoria dentro del propio DTO porque traducir exige leer la
 * enumeracion del dominio, y un {@code rest.dto} no puede depender de {@code model}: lo
 * impide {@code ArchitectureTest}, y lo impide para que el dominio pueda renombrar un
 * estado sin que el contrato publico cambie por debajo.
 *
 * <p>Recorrer el mapa da los siete en el orden de la enumeracion, que
 * {@link SellerListingsSummary} garantiza.
 */
public final class SellerListingsSummaries {

    private SellerListingsSummaries() {}

    public static SellerListingsSummaryResponse de(SellerListingsSummary resumen) {
        return new SellerListingsSummaryResponse(resumen.porEstado().entrySet().stream()
                .map(cifra -> new StatusCount(cifra.getKey().name(), cifra.getValue()))
                .toList());
    }
}
