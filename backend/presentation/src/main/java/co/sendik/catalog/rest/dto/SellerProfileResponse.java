package co.sendik.catalog.rest.dto;

import org.jspecify.annotations.Nullable;

/**
 * El vendedor, como lo ve cualquiera. HU-009, criterios 18 y 19.
 *
 * <p>Tres campos y no hay un cuarto donde meter el correo. Es la misma decision que separa
 * {@link PublicListingResponse} de {@code ListingResponse}: lo que no esta declarado no se
 * puede filtrar por descuido.
 *
 * @param id para componer el enlace a su escaparate
 * @param name el nombre visible
 * @param avatarUrl la direccion de su foto, o nulo si no tiene
 * @param verified si Sendik confirmo su identidad y su cuenta bancaria (HU-002)
 */
public record SellerProfileResponse(
        String id, String name, @Nullable String avatarUrl, boolean verified) {}
