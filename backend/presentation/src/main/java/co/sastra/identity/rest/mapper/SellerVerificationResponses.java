package co.sastra.identity.rest.mapper;

import co.sastra.identity.model.BankAccount;
import co.sastra.identity.model.IdentityDocument;
import co.sastra.identity.model.SellerVerification;
import co.sastra.identity.rest.dto.SellerVerificationResponse;

/**
 * Traduce el agregado a lo que sale por la API, dejando fuera lo que no puede salir
 * (criterio 11 de HU-002, RN-046).
 *
 * <p>La traduccion vive aqui y no en el controlador para que exista un solo sitio
 * donde mirar qué se publica de una verificacion. Con la conversion repartida entre
 * seis endpoints, el dia que alguien agregue un campo tendria que acordarse de no
 * agregarlo en seis sitios.
 */
public final class SellerVerificationResponses {

    private SellerVerificationResponses() {}

    public static SellerVerificationResponse de(SellerVerification verificacion) {
        IdentityDocument documento = verificacion.document();
        BankAccount cuenta = verificacion.bankAccount();

        return new SellerVerificationResponse(
                verificacion.status().name(),
                verificacion.attempts(),
                Math.max(SellerVerification.MAXIMO_INTENTOS - verificacion.attempts(), 0),
                verificacion.estaCompleta(),
                documento != null,
                documento == null ? null : documento.type().name(),
                // Los cuatro ultimos y nunca el numero. El objeto de valor es quien los
                // entrega, asi que aqui no hay ninguna operacion sobre el numero que
                // pudiera dejarlo entero por descuido.
                documento == null ? null : documento.number().ultimosCuatro(),
                documento == null ? null : documento.holderName().value(),
                verificacion.selfie() != null,
                cuenta == null ? null : cuenta.bank().value(),
                cuenta == null ? null : cuenta.type().name(),
                cuenta == null ? null : cuenta.number().ultimosCuatro(),
                cuenta == null ? null : cuenta.holderName().value(),
                verificacion.rejectionReason() == null
                        ? null
                        : verificacion.rejectionReason().name(),
                verificacion.rejectionNote(),
                verificacion.updatedAt().toString());
    }
}
