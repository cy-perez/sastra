package co.sastra.catalog.dto;

import co.sastra.catalog.model.ListingId;
import co.sastra.catalog.model.ListingRejectionReason;
import co.sastra.catalog.model.ModeratorId;
import org.jspecify.annotations.Nullable;

/** Criterio 22: motivo de la lista cerrada, nota opcional (RN-022). */
public record RejectListingCommand(
        ModeratorId moderador,
        ListingId publicacion,
        ListingRejectionReason motivo,
        @Nullable String nota) {}
