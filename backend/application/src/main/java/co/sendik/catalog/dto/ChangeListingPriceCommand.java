package co.sendik.catalog.dto;

import co.sendik.catalog.model.ListingId;
import co.sendik.catalog.model.SellerId;
import co.sendik.shared.money.Money;

/** Criterio 28: el precio no pasa por moderacion (RN-030, RN-062). */
public record ChangeListingPriceCommand(SellerId vendedor, ListingId publicacion, Money precio) {}
