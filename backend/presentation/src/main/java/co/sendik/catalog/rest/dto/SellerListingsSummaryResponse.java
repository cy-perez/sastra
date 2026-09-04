package co.sendik.catalog.rest.dto;

import java.util.List;

/**
 * Cuantas publicaciones tiene el vendedor en cada estado. HU-012.
 *
 * <p><strong>Una lista de pares y no un objeto con una clave por estado.</strong> Con
 * {@code {"DRAFT": 3, "PUBLISHED": 1}} cada estado nuevo de RN-061 cambiaria la forma de
 * la respuesta, y un cliente viejo no tendria como ignorarlo. Con la lista, la pantalla
 * pinta lo que conoce y se salta lo que no.
 *
 * <p>Vienen los siete, siempre, y en el orden del ciclo de vida de una publicacion. El
 * cero se dice: es lo que distingue «no tengo ninguna en revision» de «esta cifra no
 * cargo».
 *
 * <p>El estado viaja como texto y no como la enumeracion del dominio, y quien traduce es
 * {@code SellerListingsSummaries}. Un DTO de la API no depende de {@code model}, que es lo
 * que hace que el dominio pueda cambiar sin romper el contrato publico.
 */
public record SellerListingsSummaryResponse(List<StatusCount> counts) {

    /** Un estado y cuantas hay en el. */
    public record StatusCount(String status, long count) {}
}
