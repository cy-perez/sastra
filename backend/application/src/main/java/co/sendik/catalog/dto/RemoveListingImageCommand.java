package co.sendik.catalog.dto;

import co.sendik.catalog.model.ListingId;
import co.sendik.catalog.model.ProductImageId;
import co.sendik.catalog.model.SellerId;

/** Borrar una toma o una imagen de referencia. */
public record RemoveListingImageCommand(SellerId vendedor, ListingId publicacion, ProductImageId imagen) {}
