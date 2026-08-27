package co.sendik.catalog.rest.dto;

import org.jspecify.annotations.Nullable;

/**
 * Una toma o una imagen de referencia, ya con su direccion publica.
 *
 * <p>{@code kind} sale siempre y no solo cuando es de referencia: RN-066 exige que la
 * ficha rotule cada imagen de referencia como tal, y el frontend no puede rotular lo que
 * no sabe distinguir.
 *
 * <p>{@code angleDegrees} es nulo en las de referencia, que no pertenecen a la secuencia.
 */
public record ListingImageResponse(
        String id, String kind, int position, @Nullable Integer angleDegrees, String url) {}
