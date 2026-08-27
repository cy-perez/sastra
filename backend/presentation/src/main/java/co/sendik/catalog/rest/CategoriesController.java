package co.sendik.catalog.rest;

import co.sendik.catalog.dto.CategoryView;
import co.sendik.catalog.model.MeasurementKind;
import co.sendik.catalog.model.SizeSystem;
import co.sendik.catalog.rest.dto.CategoryResponse;
import co.sendik.catalog.usecase.ListCategoriesUseCase;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * El arbol de categorias. HU-007.
 *
 * <p>Lo necesita el formulario de publicar: sin el no hay desplegable que ofrecer, y de
 * la categoria elegida dependen ademas que condiciones se admiten (RN-064), que sistemas
 * de talla y que medidas se piden.
 *
 * <p><strong>Publico, sin token.</strong> Son treinta y siete nombres iguales para todo
 * el mundo y no hay nada personal que proteger; el catalogo publico, cuando llegue, pide
 * exactamente esto mismo. Pedir token aqui obligaria a tenerlo para ver de que se puede
 * hablar en Sendik.
 *
 * <p><strong>Detras de {@code FEATURE_PUBLISHING}, como el resto de la historia.</strong>
 * No porque el arbol sea secreto, sino porque el criterio 3 dice que con la bandera
 * apagada no existe ningun endpoint de esta historia, y este lo es. El dia que se encienda
 * queda disponible para el catalogo.
 */
@RestController
@RequestMapping("/api/v1/categories")
@ConditionalOnProperty(prefix = "sendik.features", name = "publishing", havingValue = "true")
public class CategoriesController {

    private final ListCategoriesUseCase casoDeListar;

    public CategoriesController(ListCategoriesUseCase casoDeListar) {
        this.casoDeListar = casoDeListar;
    }

    /**
     * El arbol activo, por familias.
     *
     * <p>Sin paginacion: son treinta y una categorias en seis familias, y esa cifra la
     * decide una migracion, no el uso. Paginar un arbol de dos niveles complicaria al
     * cliente sin ahorrarle nada.
     */
    @GetMapping
    public List<CategoryResponse> arbol() {
        return casoDeListar.execute().stream().map(CategoriesController::de).toList();
    }

    private static CategoryResponse de(CategoryView categoria) {
        return new CategoryResponse(
                categoria.id().value().toString(),
                categoria.slug(),
                categoria.nombreEs(),
                categoria.nombreEn(),
                categoria.familiaSlug(),
                categoria.sistemasDeTalla().stream().map(SizeSystem::name).toList(),
                categoria.medidasObligatorias().stream()
                        .map(MeasurementKind::name)
                        .toList(),
                categoria.admiteUsado(),
                categoria.hijas().stream().map(CategoriesController::de).toList());
    }
}
