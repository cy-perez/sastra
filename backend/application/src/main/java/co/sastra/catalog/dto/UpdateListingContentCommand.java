package co.sastra.catalog.dto;

import co.sastra.catalog.model.ListingId;
import co.sastra.catalog.model.SellerId;

/** Criterio 27: cambia lo que describe el producto, asi que puede volver a revision. */
public record UpdateListingContentCommand(SellerId vendedor, ListingId publicacion, ProductData datos) {}
