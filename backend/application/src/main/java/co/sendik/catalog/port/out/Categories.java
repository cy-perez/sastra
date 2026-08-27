package co.sendik.catalog.port.out;

import co.sendik.catalog.dto.CategoryView;
import co.sendik.catalog.model.Category;
import co.sendik.catalog.model.CategoryId;
import java.util.List;
import java.util.Optional;

/**
 * Lectura del arbol sembrado. Solo lectura: el arbol lo cambia una migracion.
 *
 * <p>Es un puerto y no una constante del codigo porque las categorias son datos
 * (docs/producto/categorias.md). Ninguna enumeracion de Java las lista.
 */
public interface Categories {

    Optional<Category> buscar(CategoryId id);

    /**
     * El arbol activo, por familias y en el orden en que se siembra.
     *
     * <p>Devuelve {@link CategoryView} y no {@code Category}: quien lo pide es una
     * pantalla y necesita los nombres visibles, que el modelo de dominio no lleva ni
     * tiene por que llevar.
     */
    List<CategoryView> arbolActivo();
}
