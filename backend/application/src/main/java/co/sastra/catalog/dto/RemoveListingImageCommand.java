package co.sastra.catalog.dto;

import co.sastra.catalog.model.ListingId;
import co.sastra.catalog.model.ProductImageId;
import co.sastra.catalog.model.SellerId;

/** Borrar una toma o una imagen de referencia. */
public record RemoveListingImageCommand(SellerId vendedor, ListingId publicacion, ProductImageId imagen) {}
