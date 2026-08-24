package co.sastra.catalog.exception;

import co.sastra.catalog.model.Condition;
import co.sastra.shared.error.DomainException;
import co.sastra.shared.error.ErrorCode;

/**
 * RN-064: esta categoria no admite lo usado.
 *
 * <p>Lo usado se vende solo en moda. En tecnologia, lo que falla no se fotografia
 * —la bateria, el sensor, la pantalla que se apaga a los dos meses— y ninguna toma a
 * 45 grados lo muestra; sin poder probar el aparato, el respaldo que promete Sastra
 * no puede sostener ese catalogo.
 */
public final class ConditionNotAllowedException extends DomainException {

    private static final long serialVersionUID = 1L;

    public ConditionNotAllowedException(Condition condicion) {
        super(
                ErrorCode.CATALOG_CONDITION_NOT_ALLOWED,
                "La categoria solo admite condicion nueva y se declaro " + condicion + " (RN-064)");
    }
}
