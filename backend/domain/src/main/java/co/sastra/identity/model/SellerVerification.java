package co.sastra.identity.model;

import co.sastra.identity.exception.AccountHolderMismatchException;
import co.sastra.identity.exception.InvalidVerificationTransitionException;
import co.sastra.identity.exception.VerificationAttemptsExhaustedException;
import co.sastra.shared.file.FileKey;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * La solicitud con la que alguien se convierte en vendedor verificado (HU-002).
 *
 * <p>Inmutable, como el resto del dominio: cada paso devuelve una instancia nueva.
 * Quien la guarda decide si eso es un {@code UPDATE} o una fila mas.
 *
 * <p><strong>Los tres datos llegan por separado y en cualquier orden.</strong> El
 * caso borde de HU-002 lo exige —«salir a la mitad del proceso: se guarda el avance
 * y se retoma donde iba»— y por eso el documento, la selfie y la cuenta son campos
 * que pueden faltar en vez de argumentos de una fabrica. Lo que no puede faltar es
 * ninguno de los tres al enviar a revision, y eso lo comprueba
 * {@link #enviarARevision(Instant)}.
 *
 * <p>Aqui no hay imagenes, solo {@link FileKey}: los bytes viven en el almacen
 * reservado y el dominio no los ve nunca (ADR-0018).
 *
 * <p>Tampoco hay numero de documento en claro hacia afuera: el objeto de valor lo
 * guarda y solo entrega los cuatro ultimos digitos (criterio 11, RN-046).
 */
public final class SellerVerification {

    /** RN-014: tres, y el cuarto exige revision manual. */
    public static final int MAXIMO_INTENTOS = 3;

    private final SellerVerificationId id;
    private final UserId userId;
    private final VerificationStatus status;

    private final @Nullable IdentityDocument document;
    private final @Nullable FileKey selfie;
    private final @Nullable BankAccount bankAccount;

    /** Envios a revision ya realizados. No cuenta las veces que se corrigio un dato. */
    private final int attempts;

    private final @Nullable RejectionReason rejectionReason;
    private final @Nullable String rejectionNote;

    private final Instant createdAt;
    private final Instant updatedAt;

    private SellerVerification(
            SellerVerificationId id,
            UserId userId,
            VerificationStatus status,
            @Nullable IdentityDocument document,
            @Nullable FileKey selfie,
            @Nullable BankAccount bankAccount,
            int attempts,
            @Nullable RejectionReason rejectionReason,
            @Nullable String rejectionNote,
            Instant createdAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "El identificador es obligatorio");
        this.userId = Objects.requireNonNull(userId, "La cuenta es obligatoria");
        this.status = Objects.requireNonNull(status, "El estado es obligatorio");
        this.document = document;
        this.selfie = selfie;
        this.bankAccount = bankAccount;
        this.attempts = attempts;
        this.rejectionReason = rejectionReason;
        this.rejectionNote = rejectionNote;
        this.createdAt = Objects.requireNonNull(createdAt, "La fecha de creacion es obligatoria");
        this.updatedAt = Objects.requireNonNull(updatedAt, "La fecha de actualizacion es obligatoria");
    }

    /**
     * Empieza el proceso. Criterio 1: que la cuenta tenga el correo verificado y sea
     * mayor de edad lo comprueba el caso de uso, que es quien ve al {@link User}.
     */
    public static SellerVerification iniciar(SellerVerificationId id, UserId userId, Instant ahora) {
        return new SellerVerification(
                id, userId, VerificationStatus.IN_PROGRESS, null, null, null, 0, null, null, ahora, ahora);
    }

    /** Reconstruye lo que hay guardado. Solo lo usa la capa de persistencia. */
    public static SellerVerification existente(
            SellerVerificationId id,
            UserId userId,
            VerificationStatus status,
            @Nullable IdentityDocument document,
            @Nullable FileKey selfie,
            @Nullable BankAccount bankAccount,
            int attempts,
            @Nullable RejectionReason rejectionReason,
            @Nullable String rejectionNote,
            Instant createdAt,
            Instant updatedAt) {
        return new SellerVerification(
                id,
                userId,
                status,
                document,
                selfie,
                bankAccount,
                attempts,
                rejectionReason,
                rejectionNote,
                createdAt,
                updatedAt);
    }

    /**
     * Guarda el documento. Solo mientras se esta llenando.
     *
     * <p>Si ya hay cuenta bancaria registrada, se vuelve a comprobar RN-012: cambiar
     * el documento puede romper una coincidencia que ya estaba bien.
     */
    public SellerVerification conDocumento(IdentityDocument documento, Instant ahora) {
        Objects.requireNonNull(documento, "El documento es obligatorio");
        exigirQuePuedaEditarse();

        if (bankAccount != null) {
            exigirTitularCoincidente(documento, bankAccount);
        }

        return copiaCon(VerificationStatus.IN_PROGRESS, documento, selfie, bankAccount, attempts, null, null, ahora);
    }

    /** Guarda la selfie. La toma en el momento la garantiza el cliente, no el dominio. */
    public SellerVerification conSelfie(FileKey selfieNueva, Instant ahora) {
        Objects.requireNonNull(selfieNueva, "La selfie es obligatoria");
        exigirQuePuedaEditarse();

        return copiaCon(
                VerificationStatus.IN_PROGRESS, document, selfieNueva, bankAccount, attempts, null, null, ahora);
    }

    /**
     * Guarda la cuenta bancaria, comprobando RN-012 si ya hay documento.
     *
     * <p>Se comprueba aqui y no solo al enviar porque casi siempre es un nombre
     * escrito de dos formas distintas y la persona lo corrige en el acto. Descubrirlo
     * dos dias despues, en el rechazo, seria el mismo error con dos dias encima.
     */
    public SellerVerification conCuentaBancaria(BankAccount cuenta, Instant ahora) {
        Objects.requireNonNull(cuenta, "La cuenta es obligatoria");
        exigirQuePuedaEditarse();

        if (document != null) {
            exigirTitularCoincidente(document, cuenta);
        }

        return copiaCon(VerificationStatus.IN_PROGRESS, document, selfie, cuenta, attempts, null, null, ahora);
    }

    /**
     * Envia a revision. Exige los tres datos y la coincidencia de RN-012.
     *
     * <p>Aqui se cuenta el intento, no al empezar a corregir: lo que RN-014 limita es
     * cuantas veces se pide una revision, no cuantas veces se toca un formulario.
     */
    public SellerVerification enviarARevision(Instant ahora) {
        exigirTransicion(VerificationStatus.PENDING_REVIEW);

        if (document == null || selfie == null || bankAccount == null) {
            throw new InvalidVerificationTransitionException(status, VerificationStatus.PENDING_REVIEW);
        }
        if (attempts >= MAXIMO_INTENTOS) {
            throw new VerificationAttemptsExhaustedException(attempts);
        }
        exigirTitularCoincidente(document, bankAccount);

        return copiaCon(
                VerificationStatus.PENDING_REVIEW, document, selfie, bankAccount, attempts + 1, null, null, ahora);
    }

    /** El moderador aprueba. Criterio 8: el rol y el sello los otorga el caso de uso. */
    public SellerVerification aprobar(Instant ahora) {
        exigirTransicion(VerificationStatus.VERIFIED);
        return copiaCon(VerificationStatus.VERIFIED, document, selfie, bankAccount, attempts, null, null, ahora);
    }

    /**
     * El moderador rechaza con un motivo de la lista cerrada y una nota opcional.
     *
     * <p>La nota viaja a la persona rechazada y <strong>nunca lleva informacion
     * judicial ni datos de un tercero</strong>. Es texto libre, asi que esa regla no
     * la puede imponer un tipo: la impone quien revisa, y esta escrita en HU-002 y en
     * el glosario para que no se pierda.
     */
    public SellerVerification rechazar(RejectionReason motivo, @Nullable String nota, Instant ahora) {
        Objects.requireNonNull(motivo, "El motivo del rechazo es obligatorio");
        exigirTransicion(VerificationStatus.REJECTED);

        return copiaCon(
                VerificationStatus.REJECTED, document, selfie, bankAccount, attempts, motivo, normalizar(nota), ahora);
    }

    /**
     * RN-013: se revoca el sello de quien ya lo tenia.
     *
     * <p>Sus publicaciones activas siguen visibles y no puede crear nuevas, pero eso
     * no se decide aqui: es del contexto de catalogo, en Fase 2.
     */
    public SellerVerification revocar(RejectionReason motivo, @Nullable String nota, Instant ahora) {
        Objects.requireNonNull(motivo, "El motivo de la revocacion es obligatorio");
        exigirTransicion(VerificationStatus.REVOKED);

        return copiaCon(
                VerificationStatus.REVOKED, document, selfie, bankAccount, attempts, motivo, normalizar(nota), ahora);
    }

    /**
     * Vuelve a llenar despues de un rechazo o una revocacion.
     *
     * <p>El limite de RN-014 se comprueba aqui y no solo al enviar: dejar que alguien
     * corrija todo el formulario para negarle el envio al final es la misma negativa
     * con el trabajo perdido en medio.
     *
     * <p>Se conservan los datos que habia. Lo que se limpia es el motivo del rechazo,
     * que ya no describe el estado.
     */
    public SellerVerification reintentar(Instant ahora) {
        exigirTransicion(VerificationStatus.IN_PROGRESS);

        if (attempts >= MAXIMO_INTENTOS) {
            throw new VerificationAttemptsExhaustedException(attempts);
        }

        return copiaCon(VerificationStatus.IN_PROGRESS, document, selfie, bankAccount, attempts, null, null, ahora);
    }

    /** Si tiene los tres datos y RN-012 se cumple. */
    public boolean estaCompleta() {
        return document != null
                && selfie != null
                && bankAccount != null
                && document.holderName().coincideCon(bankAccount.holderName());
    }

    /** Si el siguiente envio ya no cabe en RN-014. */
    public boolean agotoLosIntentos() {
        return attempts >= MAXIMO_INTENTOS;
    }

    public SellerVerificationId id() {
        return id;
    }

    public UserId userId() {
        return userId;
    }

    public VerificationStatus status() {
        return status;
    }

    public @Nullable IdentityDocument document() {
        return document;
    }

    public @Nullable FileKey selfie() {
        return selfie;
    }

    public @Nullable BankAccount bankAccount() {
        return bankAccount;
    }

    public int attempts() {
        return attempts;
    }

    public @Nullable RejectionReason rejectionReason() {
        return rejectionReason;
    }

    public @Nullable String rejectionNote() {
        return rejectionNote;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    /**
     * Editar un dato es quedarse en {@code IN_PROGRESS}, que RN-059 admite. Desde
     * {@code PENDING_REVIEW} no se edita: una solicitud enviada no se toca mientras
     * alguien la mira.
     *
     * <p><strong>Desde {@code REJECTED} o {@code REVOKED}, editar ES reintentar</strong>,
     * porque la transicion que RN-059 permite lleva a {@code IN_PROGRESS}. Asi que el
     * limite de RN-014 se comprueba tambien aqui: sin esto, alguien sin intentos podria
     * corregir el formulario entero y descubrir la negativa al enviar, que es la misma
     * negativa con el trabajo perdido en medio.
     */
    private void exigirQuePuedaEditarse() {
        exigirTransicion(VerificationStatus.IN_PROGRESS);

        boolean vieneDeUnaNegativa = status == VerificationStatus.REJECTED || status == VerificationStatus.REVOKED;

        if (vieneDeUnaNegativa && agotoLosIntentos()) {
            throw new VerificationAttemptsExhaustedException(attempts);
        }
    }

    private void exigirTransicion(VerificationStatus destino) {
        if (!status.puedePasarA(destino)) {
            throw new InvalidVerificationTransitionException(status, destino);
        }
    }

    private static void exigirTitularCoincidente(IdentityDocument documento, BankAccount cuenta) {
        if (!documento.holderName().coincideCon(cuenta.holderName())) {
            throw new AccountHolderMismatchException();
        }
    }

    /** Una nota vacia es no haber escrito nota, no una nota en blanco. */
    private static @Nullable String normalizar(@Nullable String nota) {
        if (nota == null) {
            return null;
        }
        String limpia = nota.trim();
        return limpia.isEmpty() ? null : limpia;
    }

    private SellerVerification copiaCon(
            VerificationStatus estado,
            @Nullable IdentityDocument documento,
            @Nullable FileKey selfieNueva,
            @Nullable BankAccount cuenta,
            int intentos,
            @Nullable RejectionReason motivo,
            @Nullable String nota,
            Instant ahora) {
        Objects.requireNonNull(ahora, "La fecha es obligatoria");
        return new SellerVerification(
                id, userId, estado, documento, selfieNueva, cuenta, intentos, motivo, nota, createdAt, ahora);
    }
}
