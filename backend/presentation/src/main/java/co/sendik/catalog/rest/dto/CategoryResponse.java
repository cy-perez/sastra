package co.sendik.catalog.rest.dto;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Una categoria del arbol, como la ve el formulario de publicar.
 *
 * <p>Los dos nombres viajan juntos y el cliente elige. Son cadenas cortas y el arbol
 * entero cabe en una respuesta; partirlo por idioma costaria una peticion cada vez que
 * alguien cambia de idioma.
 *
 * <p>{@code requiredMeasurements} sale calculado del grupo de medida. El frontend pinta
 * los campos que diga esta lista y no repite la tabla de grupos: una regla escrita dos
 * veces se cambia una vez.
 */
public record CategoryResponse(
        String id,
        String slug,
        String nameEs,
        String nameEn,
        @Nullable String familySlug,
        List<String> sizeSystems,
        List<String> requiredMeasurements,
        boolean allowsUsed,
        List<CategoryResponse> children) {}
