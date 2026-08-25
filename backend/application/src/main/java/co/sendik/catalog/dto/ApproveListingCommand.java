package co.sendik.catalog.dto;

import co.sendik.catalog.model.ListingId;
import co.sendik.catalog.model.ModeratorId;

/** Criterio 21. */
public record ApproveListingCommand(ModeratorId moderador, ListingId publicacion) {}
