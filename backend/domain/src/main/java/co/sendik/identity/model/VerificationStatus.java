package co.sendik.identity.model;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Estado de la verificacion de un vendedor, con sus transiciones validas (RN-059).
 *
 * <p>La tabla vive aqui y no repartida entre los metodos de
 * {@link SellerVerification} porque una transicion prohibida tiene que ser una sola
 * comprobacion en un solo sitio. Repartida, cada metodo nuevo puede olvidar la suya
 * y el olvido no se ve.
 *
 * <p><strong>{@link #REVOKED} no es {@link #REJECTED}.</strong> El primero paso la
 * revision y se le quito el sello despues (RN-013); el segundo no la paso nunca.
 * Con un solo estado para las dos cosas no se puede responder si alguien estuvo
 * verificado alguna vez, que es justo lo que hay que saber cuando sus publicaciones
 * siguen visibles y no puede crear nuevas.
 */
public enum VerificationStatus {

    /** Nunca lo intento. No es una fila: es la ausencia de una. */
    NOT_STARTED,

    /** Empezo y esta llenando o corrigiendo. Se guarda el avance y se retoma. */
    IN_PROGRESS,

    /** Enviado. Espera a un moderador y no se puede retirar. */
    PENDING_REVIEW,

    /** Aprobado: tiene el sello y el rol de vendedor. */
    VERIFIED,

    /** El moderador lo rechazo con un motivo. Puede corregir, dentro de RN-014. */
    REJECTED,

    /** Estuvo verificado y se le revoco el sello (RN-013). */
    REVOKED;

    private static final Map<VerificationStatus, Set<VerificationStatus>> PERMITIDAS =
            new EnumMap<>(VerificationStatus.class);

    static {
        PERMITIDAS.put(NOT_STARTED, EnumSet.of(IN_PROGRESS));
        // A si mismo: completar o corregir un dato no cambia de estado, y sin esta
        // entrada la comprobacion de mas abajo rechazaria guardar el avance.
        PERMITIDAS.put(IN_PROGRESS, EnumSet.of(IN_PROGRESS, PENDING_REVIEW));
        PERMITIDAS.put(PENDING_REVIEW, EnumSet.of(VERIFIED, REJECTED));
        PERMITIDAS.put(VERIFIED, EnumSet.of(REVOKED));
        PERMITIDAS.put(REJECTED, EnumSet.of(IN_PROGRESS));
        PERMITIDAS.put(REVOKED, EnumSet.of(IN_PROGRESS));
    }

    /**
     * Si RN-059 admite pasar de este estado al que se pide.
     *
     * <p>Lo que no admite, y es deliberado: salir de {@link #PENDING_REVIEW} hacia
     * atras por voluntad de la persona —una cedula ya vista no se retira— y volver a
     * {@link #NOT_STARTED}, porque ese estado significa que no hay intentos y eso ya
     * no es cierto.
     */
    public boolean puedePasarA(VerificationStatus destino) {
        return PERMITIDAS
                .getOrDefault(this, EnumSet.noneOf(VerificationStatus.class))
                .contains(destino);
    }

    /** Si tiene el sello ahora mismo. */
    public boolean esVerificado() {
        return this == VERIFIED;
    }
}
