package co.sastra.shared.rest;

import java.time.Duration;

/**
 * Llegaron demasiadas peticiones de este origen.
 *
 * <p>No hereda de {@code DomainException} a proposito: no es una regla de negocio
 * y el dominio no sabe que existen las peticiones HTTP. Vive en el borde, que es
 * quien las cuenta, y {@link ApiExceptionHandler} la traduce como cualquier otra.
 */
public class RateLimitExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient Duration espera;

    public RateLimitExceededException(Duration espera) {
        super("Demasiadas peticiones desde este origen");
        this.espera = espera;
    }

    public Duration espera() {
        return espera;
    }
}
