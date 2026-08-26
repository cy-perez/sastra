package co.sendik.catalog.dto;

import co.sendik.catalog.model.ListingId;
import co.sendik.catalog.model.SellerId;
import org.jspecify.annotations.Nullable;

/**
 * Quien pregunta por una publicacion, ademas de por cual.
 *
 * <p>{@code quienMira} es nulo cuando nadie ha iniciado sesion. No es un caso raro: la
 * lectura de una publicacion visible es la unica ruta de esta historia que responde a
 * quien no tiene cuenta, porque es la que el catalogo publico va a usar.
 *
 * <p>El rol viaja como booleano y no como {@code ModeratorId}: aqui no se decide nada
 * sobre la publicacion, solo si se puede ver. Con un identificador de moderador, la
 * firma invitaria a registrar la lectura, y leer una publicacion no se audita.
 */
public record ReadListingQuery(
        ListingId publicacion, @Nullable SellerId quienMira, boolean esModerador) {}
