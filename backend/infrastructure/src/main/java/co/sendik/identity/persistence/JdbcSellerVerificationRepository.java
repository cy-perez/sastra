package co.sendik.identity.persistence;

import co.sendik.identity.model.BankAccount;
import co.sendik.identity.model.BankAccountNumber;
import co.sendik.identity.model.BankAccountType;
import co.sendik.identity.model.BankCode;
import co.sendik.identity.model.IdentityDocument;
import co.sendik.identity.model.IdentityDocumentNumber;
import co.sendik.identity.model.IdentityDocumentType;
import co.sendik.identity.model.LegalName;
import co.sendik.identity.model.RejectionReason;
import co.sendik.identity.model.SellerVerification;
import co.sendik.identity.model.SellerVerificationId;
import co.sendik.identity.model.UserId;
import co.sendik.identity.model.VerificationStatus;
import co.sendik.identity.port.out.SellerVerificationRepository;
import co.sendik.shared.crypto.EncryptedValue;
import co.sendik.shared.file.FileKey;
import co.sendik.shared.port.out.SensitiveDataCipher;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Adaptador de persistencia de la verificacion de vendedor (HU-002).
 *
 * <p>Es el unico sitio donde el cifrado de ADR-0020 se toca: entra el agregado con
 * el numero en claro, se guardan las cuatro columnas —cifrado, version de clave,
 * huella y cuatro ultimos digitos— y al leer se descifra. Ni el caso de uso ni el
 * dominio saben que esto pasa.
 *
 * <p>Un {@code INSERT ... ON CONFLICT} y no un insert y un update separados: la
 * clave natural es la cuenta, hay una verificacion por cuenta, y desde fuera del
 * puerto la operacion es una sola, «guardar».
 */
@Repository
public class JdbcSellerVerificationRepository implements SellerVerificationRepository {

    private static final String SELECT_BASE = """
            SELECT id, user_id, status,
                   document_type, document_number_cipher, document_number_key_version,
                   document_number_last_four, document_holder_name,
                   document_front_key, document_back_key,
                   selfie_key,
                   bank_code, bank_account_type, bank_account_cipher,
                   bank_account_key_version, bank_account_holder_name,
                   attempts, rejection_reason, rejection_note,
                   created_at, updated_at
            FROM seller_verifications
            """;

    private final JdbcClient jdbc;

    private final SensitiveDataCipher cifrado;

    public JdbcSellerVerificationRepository(JdbcClient jdbc, SensitiveDataCipher cifrado) {
        this.jdbc = jdbc;
        this.cifrado = cifrado;
    }

    @Override
    public void guardar(SellerVerification verificacion) {
        IdentityDocument documento = verificacion.document();
        BankAccount cuenta = verificacion.bankAccount();

        EncryptedValue documentoCifrado =
                documento == null ? null : cifrado.cifrar(documento.number().value());
        EncryptedValue cuentaCifrada =
                cuenta == null ? null : cifrado.cifrar(cuenta.number().value());

        jdbc.sql("""
                        INSERT INTO seller_verifications (
                            id, user_id, status,
                            document_type, document_number_cipher, document_number_key_version,
                            document_number_lookup, document_number_last_four, document_holder_name,
                            document_front_key, document_back_key,
                            selfie_key,
                            bank_code, bank_account_type, bank_account_cipher,
                            bank_account_key_version, bank_account_last_four, bank_account_holder_name,
                            attempts, rejection_reason, rejection_note,
                            created_at, updated_at)
                        VALUES (
                            :id, :usuario, :estado,
                            :tipoDocumento, :documentoCifrado, :versionDocumento,
                            :huellaDocumento, :ultimosDocumento, :titularDocumento,
                            :frente, :reverso,
                            :selfie,
                            :banco, :tipoCuenta, :cuentaCifrada,
                            :versionCuenta, :ultimosCuenta, :titularCuenta,
                            :intentos, :motivo, :nota,
                            :creado, :actualizado)
                        ON CONFLICT (user_id) DO UPDATE SET
                            status                      = EXCLUDED.status,
                            document_type               = EXCLUDED.document_type,
                            document_number_cipher      = EXCLUDED.document_number_cipher,
                            document_number_key_version = EXCLUDED.document_number_key_version,
                            document_number_lookup      = EXCLUDED.document_number_lookup,
                            document_number_last_four   = EXCLUDED.document_number_last_four,
                            document_holder_name        = EXCLUDED.document_holder_name,
                            document_front_key          = EXCLUDED.document_front_key,
                            document_back_key           = EXCLUDED.document_back_key,
                            selfie_key                  = EXCLUDED.selfie_key,
                            bank_code                   = EXCLUDED.bank_code,
                            bank_account_type           = EXCLUDED.bank_account_type,
                            bank_account_cipher         = EXCLUDED.bank_account_cipher,
                            bank_account_key_version    = EXCLUDED.bank_account_key_version,
                            bank_account_last_four      = EXCLUDED.bank_account_last_four,
                            bank_account_holder_name    = EXCLUDED.bank_account_holder_name,
                            attempts                    = EXCLUDED.attempts,
                            rejection_reason            = EXCLUDED.rejection_reason,
                            rejection_note              = EXCLUDED.rejection_note,
                            updated_at                  = EXCLUDED.updated_at
                        """)
                .param("id", verificacion.id().value())
                .param("usuario", verificacion.userId().value())
                .param("estado", verificacion.status().name())
                .param(
                        "tipoDocumento",
                        documento == null ? null : documento.type().name())
                .param("documentoCifrado", documentoCifrado == null ? null : documentoCifrado.cipher())
                .param("versionDocumento", documentoCifrado == null ? null : documentoCifrado.keyVersion())
                .param(
                        "huellaDocumento",
                        documento == null
                                ? null
                                : cifrado.huella(documento.number().value()))
                .param(
                        "ultimosDocumento",
                        documento == null ? null : documento.number().ultimosCuatro())
                .param(
                        "titularDocumento",
                        documento == null ? null : documento.holderName().value())
                .param(
                        "frente",
                        documento == null ? null : documento.frontImage().value())
                .param(
                        "reverso",
                        documento == null ? null : documento.backImage().value())
                .param(
                        "selfie",
                        verificacion.selfie() == null
                                ? null
                                : verificacion.selfie().value())
                .param("banco", cuenta == null ? null : cuenta.bank().value())
                .param("tipoCuenta", cuenta == null ? null : cuenta.type().name())
                .param("cuentaCifrada", cuentaCifrada == null ? null : cuentaCifrada.cipher())
                .param("versionCuenta", cuentaCifrada == null ? null : cuentaCifrada.keyVersion())
                .param("ultimosCuenta", cuenta == null ? null : cuenta.number().ultimosCuatro())
                .param(
                        "titularCuenta",
                        cuenta == null ? null : cuenta.holderName().value())
                .param("intentos", verificacion.attempts())
                .param(
                        "motivo",
                        verificacion.rejectionReason() == null
                                ? null
                                : verificacion.rejectionReason().name())
                .param("nota", verificacion.rejectionNote())
                .param("creado", Timestamp.from(verificacion.createdAt()))
                .param("actualizado", Timestamp.from(verificacion.updatedAt()))
                .update();
    }

    @Override
    public Optional<SellerVerification> buscarPorUsuario(UserId usuario) {
        return jdbc.sql(SELECT_BASE + " WHERE user_id = :usuario")
                .param("usuario", usuario.value())
                .query(this::mapear)
                .optional();
    }

    @Override
    public Optional<SellerVerification> buscarPorId(SellerVerificationId verificacion) {
        return jdbc.sql(SELECT_BASE + " WHERE id = :id")
                .param("id", verificacion.value())
                .query(this::mapear)
                .optional();
    }

    /**
     * Compara por la huella, que es lo unico comparable: el cifrado produce un texto
     * distinto cada vez a proposito, asi que un {@code WHERE} sobre la columna cifrada
     * no encontraria nada nunca (ADR-0020).
     *
     * <p>Solo {@code VERIFIED}, que es la lectura literal del criterio 5: lo que no
     * puede haber son dos cuentas verificadas con el mismo documento. Dos en revision
     * si pueden existir, y las resuelve el moderador.
     */
    @Override
    public boolean existeOtraVerificadaConDocumento(String numeroDeDocumento, UserId exceptoEstaCuenta) {
        return jdbc.sql("""
                        SELECT EXISTS (
                            SELECT 1 FROM seller_verifications
                            WHERE document_number_lookup = :huella
                              AND status = 'VERIFIED'
                              AND user_id <> :excepto)
                        """)
                .param("huella", cifrado.huella(numeroDeDocumento))
                .param("excepto", exceptoEstaCuenta.value())
                .query(Boolean.class)
                .single();
    }

    /**
     * Usa el indice parcial de V8 sobre {@code updated_at} para las pendientes: sin el,
     * esta consulta recorreria la tabla entera para encontrar las pocas que esperan.
     */
    @Override
    public List<SellerVerification> pendientesDeRevision(int limite) {
        return jdbc.sql(SELECT_BASE + " WHERE status = 'PENDING_REVIEW' ORDER BY updated_at ASC LIMIT :limite")
                .param("limite", limite)
                .query(this::mapear)
                .list();
    }

    private SellerVerification mapear(ResultSet fila, int numero) throws SQLException {
        return SellerVerification.existente(
                new SellerVerificationId(fila.getObject("id", java.util.UUID.class)),
                new UserId(fila.getObject("user_id", java.util.UUID.class)),
                VerificationStatus.valueOf(fila.getString("status")),
                leerDocumento(fila),
                clave(fila.getString("selfie_key")),
                leerCuenta(fila),
                fila.getInt("attempts"),
                motivo(fila.getString("rejection_reason")),
                fila.getString("rejection_note"),
                fila.getTimestamp("created_at").toInstant(),
                fila.getTimestamp("updated_at").toInstant());
    }

    private @Nullable IdentityDocument leerDocumento(ResultSet fila) throws SQLException {
        String tipo = fila.getString("document_type");
        String cifradoDelNumero = fila.getString("document_number_cipher");
        if (tipo == null || cifradoDelNumero == null) {
            return null;
        }

        String numero =
                cifrado.descifrar(new EncryptedValue(cifradoDelNumero, fila.getInt("document_number_key_version")));

        return new IdentityDocument(
                IdentityDocumentType.valueOf(tipo),
                new IdentityDocumentNumber(numero),
                new LegalName(fila.getString("document_holder_name")),
                new FileKey(fila.getString("document_front_key")),
                new FileKey(fila.getString("document_back_key")));
    }

    private @Nullable BankAccount leerCuenta(ResultSet fila) throws SQLException {
        String banco = fila.getString("bank_code");
        String cifradoDeLaCuenta = fila.getString("bank_account_cipher");
        if (banco == null || cifradoDeLaCuenta == null) {
            return null;
        }

        String numero =
                cifrado.descifrar(new EncryptedValue(cifradoDeLaCuenta, fila.getInt("bank_account_key_version")));

        return new BankAccount(
                new BankCode(banco),
                BankAccountType.valueOf(fila.getString("bank_account_type")),
                new BankAccountNumber(numero),
                new LegalName(fila.getString("bank_account_holder_name")));
    }

    private static @Nullable FileKey clave(@Nullable String valor) {
        return valor == null ? null : new FileKey(valor);
    }

    private static @Nullable RejectionReason motivo(@Nullable String valor) {
        return valor == null ? null : RejectionReason.valueOf(valor);
    }
}
