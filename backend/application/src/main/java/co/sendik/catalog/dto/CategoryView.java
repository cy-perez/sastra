package co.sendik.catalog.dto;

import co.sendik.catalog.model.CategoryId;
import co.sendik.catalog.model.MeasurementKind;
import co.sendik.catalog.model.SizeSystem;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Una categoria del arbol, tal como la necesita quien va a publicar.
 *
 * <p><strong>Es una vista de lectura y no la entidad {@code Category}.</strong> El modelo
 * de dominio no lleva los nombres visibles, y no debe: son texto para una pantalla, no
 * una regla. Aqui si van, porque el formulario tiene que pintar un desplegable.
 *
 * <p><strong>Las medidas obligatorias viajan calculadas.</strong> Salen del grupo de
 * medida, que es una regla del dominio ({@code MeasurementGroup.obligatorias}). Mandar
 * solo el nombre del grupo obligaria al frontend a repetir esa tabla, y una regla escrita
 * dos veces se cambia una vez.
 *
 * <p>Los dos idiomas viajan juntos y el cliente elige. Son sesenta y dos cadenas cortas
 * para todo el arbol: partirlo por idioma costaria una peticion por cambio de idioma para
 * ahorrar unos bytes.
 */
public record CategoryView(
        CategoryId id,
        String slug,
        String nombreEs,
        String nombreEn,
        @Nullable String familiaSlug,
        Set<SizeSystem> sistemasDeTalla,
        Set<MeasurementKind> medidasObligatorias,
        boolean admiteUsado,
        List<CategoryView> hijas) {

    public boolean esFamilia() {
        return familiaSlug == null;
    }
}
