package co.sastra.catalog.dto;

import co.sastra.catalog.model.ListingId;
import co.sastra.catalog.model.SellerId;

/**
 * Lo que el vendedor hace sobre su publicacion sin mandar datos: enviar a revision,
 * retirarla, pausar, reanudar, archivar.
 *
 * <p>Un comando y no cinco identicos: lo que distingue esas acciones es el caso de uso
 * que las ejecuta, no su entrada.
 */
public record SellerListingCommand(SellerId vendedor, ListingId publicacion) {}
