package co.sendik.catalog.dto;

import co.sendik.catalog.model.SellerId;

/** Criterio 4: crear el borrador. */
public record CreateListingCommand(SellerId vendedor, ProductData datos) {}
