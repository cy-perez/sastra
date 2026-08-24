package co.sastra.catalog.dto;

import co.sastra.catalog.model.ListingId;
import co.sastra.catalog.model.ModeratorId;

/** Criterio 21. */
public record ApproveListingCommand(ModeratorId moderador, ListingId publicacion) {}
