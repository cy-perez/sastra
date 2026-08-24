package co.sastra.catalog.exception;

import co.sastra.catalog.model.MeasurementGroup;
import co.sastra.catalog.model.MeasurementKind;
import co.sastra.shared.error.DomainException;
import co.sastra.shared.error.ErrorCode;
import java.util.Set;

/**
 * RN-021: faltan medidas obligatorias del grupo de la categoria.
 *
 * <p>Codigo de validacion y no uno propio del catalogo: para el cliente esto es un
 * formulario incompleto, y el detalle por campo viaja en {@code errors}, que es donde
 * el contrato dice que va.
 */
public final class MeasurementsIncompleteException extends DomainException {

    private static final long serialVersionUID = 1L;

    private final transient Set<MeasurementKind> faltantes;

    public MeasurementsIncompleteException(MeasurementGroup grupo, Set<MeasurementKind> faltantes) {
        super(ErrorCode.COMMON_VALIDATION_FAILED, "Faltan medidas del grupo " + grupo + ": " + faltantes);
        this.faltantes = Set.copyOf(faltantes);
    }

    /** Las que faltan, para que el borde arme una entrada por campo. */
    public Set<MeasurementKind> faltantes() {
        return faltantes;
    }
}
