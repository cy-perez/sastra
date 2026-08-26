package co.sendik.catalog.dto;

import co.sendik.catalog.model.ListingId;
import co.sendik.catalog.model.SellerId;
import co.sendik.catalog.model.ShippingDimensions;

public record ChangeListingShippingCommand(SellerId vendedor, ListingId publicacion, ShippingDimensions envio) {}
