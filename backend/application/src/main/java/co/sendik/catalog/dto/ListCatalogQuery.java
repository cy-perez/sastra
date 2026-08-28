package co.sendik.catalog.dto;

import co.sendik.catalog.model.CategoryId;
import org.jspecify.annotations.Nullable;

/**
 * Un tramo del catalogo publico. HU-009, criterios 1, 3 y 8.
 *
 * <p>El tope vive aqui y no en el controlador, por lo mismo que el de la cola del
 * moderador: escrito en el borde protegeria esa ruta y ninguna otra que use el mismo caso
 * de uso. Un {@code limite} por encima del tope se rechaza y no se recorta en silencio,
 * porque quien pide 500 y recibe 24 sin que nadie se lo diga cree que ya tiene todo.
 *
 * @param categoria donde buscar, o nulo para todo el catalogo. Puede ser una familia: el
 *     caso de uso resuelve las categorias publicables que cuelgan de ella, porque no se
 *     publica en una familia sino en una categoria suya
 * @param desde por donde seguir, o nulo para el primer tramo
 * @param limite cuantas se piden como maximo
 */
public record ListCatalogQuery(
        @Nullable CategoryId categoria, @Nullable CatalogCursor desde, int limite) {

    /** El mismo tope que el resto de los listados del contrato. */
    public static final int LIMITE_MAXIMO = 50;

    public static final int LIMITE_POR_OMISION = 24;

    public ListCatalogQuery {
        if (limite < 1 || limite > LIMITE_MAXIMO) {
            throw new IllegalArgumentException("El limite va entre 1 y " + LIMITE_MAXIMO + ", y llego " + limite);
        }
    }
}
