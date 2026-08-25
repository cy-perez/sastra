package co.sendik.catalog.exception;

import co.sendik.shared.error.DomainException;
import co.sendik.shared.error.ErrorCode;
import java.util.List;

/**
 * Faltan datos para enviar el borrador a revision. Criterio 6.
 *
 * <p>Lleva la lista entera y no el primero que falto, porque el criterio pide una
 * entrada por cada campo. Un vendedor que descubre lo que falta de uno en uno hace
 * siete viajes al formulario.
 *
 * <p>422 y no 400: lo que se envio se entiende, y lo que el negocio rechaza es enviar a
 * revision algo incompleto. Guardar el borrador asi es perfectamente valido.
 */
public final class IncompleteListingException extends DomainException {

    private static final long serialVersionUID = 1L;

    private final transient List<String> faltantes;

    public IncompleteListingException(List<String> faltantes) {
        super(ErrorCode.CATALOG_LISTING_INCOMPLETE, "Faltan datos para enviar a revision: " + faltantes);
        this.faltantes = List.copyOf(faltantes);
    }

    /** Los campos que faltan, para que el borde arme una entrada por cada uno. */
    public List<String> faltantes() {
        return faltantes;
    }
}
