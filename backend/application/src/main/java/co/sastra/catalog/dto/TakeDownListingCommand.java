package co.sastra.catalog.dto;

import co.sastra.catalog.model.ListingId;
import co.sastra.catalog.model.ListingRejectionReason;
import co.sastra.catalog.model.ModeratorId;
import org.jspecify.annotations.Nullable;

/** Criterio 31: el moderador baja algo que ya era visible por infringir RN-024. */
public record TakeDownListingCommand(
        ModeratorId moderador,
        ListingId publicacion,
        ListingRejectionReason motivo,
        @Nullable String nota) {}
