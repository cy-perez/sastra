package co.sastra.identity.port.out;

import co.sastra.identity.model.TokenPurpose;
import co.sastra.identity.model.UserId;
import co.sastra.identity.model.VerificationToken;
import java.time.Instant;
import java.util.Optional;

/** Puerto de salida hacia el almacen de tokens de un solo uso. */
public interface VerificationTokenRepository {

    void guardar(VerificationToken token);

    void actualizar(VerificationToken token);

    /**
     * Se busca por hash y nunca por el valor en claro: el token que llega en el
     * enlace se hashea antes de consultar, asi que la base nunca ve el original.
     */
    Optional<VerificationToken> buscarPorHash(String tokenHash);

    /** Cuenta los emitidos desde un instante. Es lo que sostiene el limite de reenvios. */
    int contarEmitidosDesde(UserId usuario, TokenPurpose proposito, Instant desde);
}
