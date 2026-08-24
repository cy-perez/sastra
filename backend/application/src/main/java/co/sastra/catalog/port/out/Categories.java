package co.sastra.catalog.port.out;

import co.sastra.catalog.model.Category;
import co.sastra.catalog.model.CategoryId;
import java.util.Optional;

/**
 * Lectura del arbol sembrado. Solo lectura: el arbol lo cambia una migracion.
 *
 * <p>Es un puerto y no una constante del codigo porque las categorias son datos
 * (docs/producto/categorias.md). Ninguna enumeracion de Java las lista.
 */
public interface Categories {

    Optional<Category> buscar(CategoryId id);
}
