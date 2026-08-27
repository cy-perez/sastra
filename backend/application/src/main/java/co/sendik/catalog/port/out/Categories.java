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

    /**
     * Donde se publica, colgando de esta categoria. HU-009, criterios 9 y 10.
     *
     * <p>Si es una hoja activa, ella misma. Si es una familia, sus hojas activas, porque
     * no se publica en una familia sino en una categoria suya. Si no existe o esta
     * retirada del arbol, vacio: quien pregunta lo convierte en 404, que es distinto de un
     * listado vacio y no se confunde con «esta categoria no tiene nada».
     */
    List<CategoryId> publicablesBajo(CategoryId id);
}
