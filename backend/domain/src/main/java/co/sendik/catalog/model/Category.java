package co.sendik.catalog.model;

import co.sendik.catalog.exception.ConditionNotAllowedException;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Una categoria del arbol. Fuente de verdad: docs/producto/categorias.md.
 *
 * <p>Es dominio y no configuracion suelta porque decide tres cosas de negocio: con
 * que escalas se declara la talla, que medidas son obligatorias y si admite lo usado.
 * Que las filas vivan en una tabla sembrada es un detalle de donde se guardan.
 *
 * <p>Las familias —el primer nivel— no llevan grupo de medida ni sistemas de talla:
 * no se publica en una familia, se publica en una categoria suya.
 */
public record Category(
        CategoryId id,
        String slug,
        @Nullable CategoryId parentId,
        Set<SizeSystem> sizeSystems,
        @Nullable MeasurementGroup measurementGroup,
        boolean allowsUsed,
        boolean active) {

    public Category {
        Objects.requireNonNull(id, "El identificador es obligatorio");
        Objects.requireNonNull(slug, "El slug es obligatorio");
        Objects.requireNonNull(sizeSystems, "Los sistemas de talla son obligatorios");

        sizeSystems = Set.copyOf(sizeSystems);

        boolean esFamilia = parentId == null;
        if (esFamilia && (measurementGroup != null || !sizeSystems.isEmpty())) {
            throw new IllegalArgumentException("Una familia no declara talla ni medidas: " + slug);
        }
        if (!esFamilia && (measurementGroup == null || sizeSystems.isEmpty())) {
            throw new IllegalArgumentException("Una categoria necesita grupo de medida y al menos una talla: " + slug);
        }
    }

    public boolean esFamilia() {
        return parentId == null;
    }

    /** Donde si se puede publicar. */
    public boolean admitePublicaciones() {
        return !esFamilia() && active;
    }

    /**
     * RN-064.
     *
     * @throws ConditionNotAllowedException si la categoria no admite lo usado y la
     *     condicion declarada no es nueva
     */
    public void exigirCondicionAdmisible(Condition condicion) {
        Objects.requireNonNull(condicion, "La condicion es obligatoria");
        if (!allowsUsed && !condicion.esNueva()) {
            throw new ConditionNotAllowedException(condicion);
        }
    }

    /** Las condiciones que esta categoria admite, para que el borde arme el formulario. */
    public Set<Condition> condicionesAdmisibles() {
        return allowsUsed ? Set.of(Condition.values()) : Set.of(Condition.NEW);
    }

    /** El grupo de medida de una hoja. Solo se llama sobre una categoria publicable. */
    public MeasurementGroup grupoDeMedida() {
        if (measurementGroup == null) {
            throw new IllegalStateException("Una familia no tiene grupo de medida: " + slug);
        }
        return measurementGroup;
    }
}
