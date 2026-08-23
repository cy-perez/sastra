package co.sastra.identity.rest.mapper;

import co.sastra.identity.model.BankAccount;
import co.sastra.identity.model.IdentityDocument;
import co.sastra.identity.model.SellerVerification;
import co.sastra.identity.model.UserId;
import co.sastra.identity.rest.dto.PendingVerificationResponse;

/**
 * Traduce una solicitud a lo que ve el moderador en su bandeja, con las mismas
 * restricciones del criterio 11 que rigen para su dueno.
 */
public final class PendingVerificationResponses {

    private PendingVerificationResponses() {}

    public static PendingVerificationResponse de(SellerVerification verificacion, UserId quienMira) {
        IdentityDocument documento = verificacion.document();
        BankAccount cuenta = verificacion.bankAccount();

        return new PendingVerificationResponse(
                verificacion.id().toString(),
                verificacion.attempts(),
                documento == null ? null : documento.type().name(),
                documento == null ? null : documento.number().ultimosCuatro(),
                documento == null ? null : documento.holderName().value(),
                documento != null,
                verificacion.selfie() != null,
                cuenta == null ? null : cuenta.bank().value(),
                cuenta == null ? null : cuenta.type().name(),
                cuenta == null ? null : cuenta.number().ultimosCuatro(),
                cuenta == null ? null : cuenta.holderName().value(),
                verificacion.updatedAt().toString(),
                // RN-060. Se calcula aqui y no se manda el dueno: la pantalla necesita
                // saber si es suya, no de quien es.
                verificacion.userId().equals(quienMira));
    }
}
