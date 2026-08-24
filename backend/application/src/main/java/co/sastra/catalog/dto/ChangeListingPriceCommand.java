package co.sastra.catalog.dto;

import co.sastra.catalog.model.ListingId;
import co.sastra.catalog.model.SellerId;
import co.sastra.shared.money.Money;

/** Criterio 28: el precio no pasa por moderacion (RN-030, RN-062). */
public record ChangeListingPriceCommand(SellerId vendedor, ListingId publicacion, Money precio) {}
