package co.sastra.identity.exception;

import co.sastra.shared.error.DomainException;
import co.sastra.shared.error.ErrorCode;
import java.time.Instant;
import java.util.Objects;

/**
 * RN-006: la cuenta esta bloqueada por intentos fallidos.
 *
 * <p>Lleva el instante de desbloqueo porque el borde HTTP lo necesita para la
 * cabecera {@code Retry-After} (docs/arquitectura/contrato-api.md). Es una
 * duracion, no una hora del reloj del servidor: la primera no dice nada sobre la
 * infraestructura y la segunda si.
 */
public final class AccountLockedException extends DomainException {

    private static final long serialVersionUID = 1L;

    private final Instant desbloqueoEn;

    public AccountLockedException(Instant desbloqueoEn) {
        super(ErrorCode.AUTH_ACCOUNT_LOCKED, "La cuenta esta bloqueada por intentos fallidos");
        this.desbloqueoEn = Objects.requireNonNull(desbloqueoEn, "El instante de desbloqueo es obligatorio");
    }

    public Instant desbloqueoEn() {
        return desbloqueoEn;
    }
}
