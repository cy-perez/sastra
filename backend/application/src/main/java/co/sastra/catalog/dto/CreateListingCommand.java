package co.sastra.catalog.dto;

import co.sastra.catalog.model.SellerId;

/** Criterio 4: crear el borrador. */
public record CreateListingCommand(SellerId vendedor, ProductData datos) {}
