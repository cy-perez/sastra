package co.sendik.catalog.usecase;

import co.sendik.catalog.dto.CategoryView;
import co.sendik.catalog.port.out.Categories;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * El arbol de categorias activas, para el formulario de publicar. HU-007.
 *
 * <p>No decide nada: el arbol lo siembra una migracion y no hay regla que aplicar sobre
 * el. Existe como caso de uso porque entre el controlador y el repositorio va siempre uno
 * —lo exige {@code ArchitectureTest}— y porque el dia que el catalogo publico necesite lo
 * mismo, lo pide aqui y no vuelve a escribir la consulta.
 *
 * <p>Devuelve solo lo activo. Una categoria retirada sigue existiendo para las
 * publicaciones que ya la tenian, pero no se puede elegir en una nueva (caso borde de la
 * historia).
 */
public class ListCategoriesUseCase {

    private final Categories categorias;

    public ListCategoriesUseCase(Categories categorias) {
        this.categorias = categorias;
    }

    @Transactional
    public List<CategoryView> execute() {
        return categorias.arbolActivo();
    }
}
