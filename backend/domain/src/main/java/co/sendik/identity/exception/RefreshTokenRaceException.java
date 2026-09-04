package co.sendik.identity.exception;

import co.sendik.shared.error.DomainException;
import co.sendik.shared.error.ErrorCode;

/**
 * RN-007: el token presentado lo acaba de rotar otra peticion del mismo cliente.
 *
 * <p>**Se rechaza igual que uno invalido, y no significa lo mismo.** La cookie que el
 * navegador tiene ahora es la buena; lo unico que pasa es que esta peticion llego con la
 * anterior. Por eso lleva codigo propio: quien lo reciba tiene que reintentar, no cerrar
 * la sesion (ADR-0030).
 *
 * <p>No se emite nada aqui a proposito. Emitir en la carrera pondria dos tokens validos en
 * circulacion, que es exactamente lo que la rotacion existe para que no ocurra.
 */
public final class RefreshTokenRaceException extends DomainException {

    private static final long serialVersionUID = 1L;

    public RefreshTokenRaceException() {
        super(ErrorCode.AUTH_SESSION_RACE, "El token de refresco lo acaba de rotar otra peticion");
    }
}
