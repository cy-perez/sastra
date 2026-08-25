package co.sendik.catalog.dto;

import co.sendik.catalog.model.ImageKind;
import co.sendik.catalog.model.ListingId;
import co.sendik.catalog.model.SellerId;

/**
 * Criterio 14: una imagen, con su posicion y su clase.
 *
 * <p>{@code desdeGaleria} lo declara el cliente y el servidor no lo puede comprobar,
 * asi que solo suma la marca de revision mas atenta y nunca quita una validacion
 * (criterio 18).
 */
public record UploadListingImageCommand(
        SellerId vendedor,
        ListingId publicacion,
        ImageKind clase,
        int posicion,
        byte[] contenido,
        boolean desdeGaleria) {}
