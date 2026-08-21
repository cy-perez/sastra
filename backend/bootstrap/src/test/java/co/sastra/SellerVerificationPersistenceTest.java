package co.sastra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.sastra.identity.model.BankAccount;
import co.sastra.identity.model.BankAccountNumber;
import co.sastra.identity.model.BankAccountType;
import co.sastra.identity.model.BankCode;
import co.sastra.identity.model.BirthDate;
import co.sastra.identity.model.DisplayName;
import co.sastra.identity.model.Email;
import co.sastra.identity.model.IdentityDocument;
import co.sastra.identity.model.IdentityDocumentNumber;
import co.sastra.identity.model.IdentityDocumentType;
import co.sastra.identity.model.LegalName;
import co.sastra.identity.model.RawPassword;
import co.sastra.identity.model.RejectionReason;
import co.sastra.identity.model.SellerVerification;
import co.sastra.identity.model.SellerVerificationId;
import co.sastra.identity.model.User;
import co.sastra.identity.model.UserId;
import co.sastra.identity.model.UserLocale;
import co.sastra.identity.model.VerificationStatus;
import co.sastra.identity.port.out.PasswordHasher;
import co.sastra.identity.port.out.SellerVerificationRepository;
import co.sastra.identity.port.out.UserRepository;
import co.sastra.shared.file.FileKey;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * La verificacion de vendedor contra PostgreSQL 17 real. HU-002 rebanada B.
 *
 * <p>Estas tres cosas no se pueden dar por buenas con simulaciones, y las tres las
 * pide la historia en sus pruebas requeridas:
 *
 * <ul>
 *   <li>Que el cifrado sea <strong>efectivo en la base</strong>. Un doble del cifrador
 *       diria que si sin que nada se cifre; lo unico que lo demuestra es leer la
 *       columna con SQL y no encontrar el numero.
 *   <li>Que la unicidad del documento la garantice la base y no el codigo. Bajo
 *       concurrencia, dos transacciones pueden comprobar «no existe» a la vez y las
 *       dos creer que pueden aprobar: lo que las separa es el indice unico.
 *   <li>Que el agregado sobreviva la ida y vuelta entero, con sus tres partes
 *       opcionales y su numero descifrado.
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(PostgresTestContainer.class)
class SellerVerificationPersistenceTest {

    private static final String CONTRASENA = "una-contrasena-larga-de-prueba";

    /**
     * Documento distinto en cada prueba, por el mismo motivo que el correo: el
     * contenedor es uno para toda la clase, y el indice unico del criterio 5 es de
     * toda la tabla. Con una cedula constante, la primera prueba que aprueba deja el
     * documento tomado y las siguientes fallan por culpa de la anterior.
     */
    private static final AtomicLong SECUENCIA = new AtomicLong(1_000_000);

    private static final String CUENTA = "91500123456";

    private static String cedulaNueva() {
        return "10" + SECUENCIA.incrementAndGet();
    }

    private static final LegalName TITULAR = new LegalName("Ana Maria Garcia");

    private final SellerVerificationRepository verificaciones;
    private final UserRepository usuarios;
    private final PasswordHasher hasher;
    private final JdbcClient jdbc;
    private final Clock reloj;

    SellerVerificationPersistenceTest(
            SellerVerificationRepository verificaciones,
            UserRepository usuarios,
            PasswordHasher hasher,
            JdbcClient jdbc,
            Clock reloj) {
        this.verificaciones = verificaciones;
        this.usuarios = usuarios;
        this.hasher = hasher;
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    /** Correo distinto en cada prueba: el contenedor es uno para toda la clase (RN-001). */
    private UserId cuentaNueva() {
        User nueva = User.registrar(
                UserId.nuevo(),
                new Email("ana-" + UUID.randomUUID() + "@correo.co"),
                new DisplayName("Ana Maria"),
                new BirthDate(LocalDate.of(1990, 3, 4)),
                UserLocale.ES,
                LocalDate.now(reloj),
                reloj.instant());

        usuarios.crear(nueva, hasher.hashear(new RawPassword(CONTRASENA)));
        return nueva.id();
    }

    private SellerVerification completaDe(UserId usuario, String cedula) {
        Instant ahora = reloj.instant();

        return SellerVerification.iniciar(SellerVerificationId.nuevo(), usuario, ahora)
                .conDocumento(
                        new IdentityDocument(
                                IdentityDocumentType.CC,
                                new IdentityDocumentNumber(cedula),
                                TITULAR,
                                new FileKey("documentos/frente-" + UUID.randomUUID() + ".jpg"),
                                new FileKey("documentos/reverso-" + UUID.randomUUID() + ".jpg")),
                        ahora)
                .conSelfie(new FileKey("selfies/" + UUID.randomUUID() + ".jpg"), ahora)
                .conCuentaBancaria(
                        new BankAccount(
                                new BankCode("bancolombia"),
                                BankAccountType.SAVINGS,
                                new BankAccountNumber(CUENTA),
                                TITULAR),
                        ahora);
    }

    @Test
    void deberia_guardar_y_devolver_el_agregado_entero() {
        UserId usuario = cuentaNueva();
        String cedula = cedulaNueva();
        SellerVerification guardada = completaDe(usuario, cedula);

        verificaciones.guardar(guardada);
        SellerVerification leida = verificaciones.buscarPorUsuario(usuario).orElseThrow();

        assertThat(leida.id()).isEqualTo(guardada.id());
        assertThat(leida.status()).isEqualTo(VerificationStatus.IN_PROGRESS);
        assertThat(leida.document()).isNotNull();
        assertThat(leida.document().number().value()).isEqualTo(cedula);
        assertThat(leida.document().holderName().value()).isEqualTo(TITULAR.value());
        assertThat(leida.bankAccount()).isNotNull();
        assertThat(leida.bankAccount().number().value()).isEqualTo(CUENTA);
        assertThat(leida.selfie()).isNotNull();
        assertThat(leida.estaCompleta()).isTrue();
    }

    /** El caso borde de HU-002: se guarda el avance a medias y se retoma. */
    @Test
    void deberia_guardar_una_verificacion_a_medias() {
        UserId usuario = cuentaNueva();
        Instant ahora = reloj.instant();

        verificaciones.guardar(SellerVerification.iniciar(SellerVerificationId.nuevo(), usuario, ahora)
                .conSelfie(new FileKey("selfies/" + UUID.randomUUID() + ".jpg"), ahora));

        SellerVerification leida = verificaciones.buscarPorUsuario(usuario).orElseThrow();

        assertThat(leida.selfie()).isNotNull();
        assertThat(leida.document()).isNull();
        assertThat(leida.bankAccount()).isNull();
        assertThat(leida.estaCompleta()).isFalse();
    }

    @Test
    void deberia_actualizar_en_vez_de_crear_una_segunda_fila() {
        UserId usuario = cuentaNueva();
        String cedula = cedulaNueva();
        SellerVerification inicial = completaDe(usuario, cedula);

        verificaciones.guardar(inicial);
        verificaciones.guardar(inicial.enviarARevision(reloj.instant()));

        Integer filas = jdbc.sql("SELECT count(*) FROM seller_verifications WHERE user_id = :usuario")
                .param("usuario", usuario.value())
                .query(Integer.class)
                .single();

        assertThat(filas).isEqualTo(1);
        assertThat(verificaciones.buscarPorUsuario(usuario).orElseThrow().status())
                .isEqualTo(VerificationStatus.PENDING_REVIEW);
    }

    // --- RN-046 y ADR-0020 ---------------------------------------------------

    /**
     * Lo unico que demuestra que el cifrado es efectivo: leer las columnas con SQL,
     * como lo haria un volcado de la base o quien la administre, y no encontrar el
     * numero en ninguna.
     */
    @Test
    void deberia_cumplir_RN_046_no_guardando_ningun_numero_en_claro() {
        UserId usuario = cuentaNueva();
        String cedula = cedulaNueva();
        verificaciones.guardar(completaDe(usuario, cedula));

        List<String> columnas = jdbc.sql("""
                        SELECT document_number_cipher, document_number_last_four,
                               bank_account_cipher, bank_account_last_four,
                               document_holder_name, bank_account_holder_name
                        FROM seller_verifications WHERE user_id = :usuario
                        """)
                .param("usuario", usuario.value())
                .query((fila, numero) -> List.of(
                        fila.getString(1),
                        fila.getString(2),
                        fila.getString(3),
                        fila.getString(4),
                        fila.getString(5),
                        fila.getString(6)))
                .single();

        assertThat(columnas).noneMatch(valor -> valor.contains(cedula));
        assertThat(columnas).noneMatch(valor -> valor.contains(CUENTA));

        // Y los cuatro ultimos si estan, porque es lo que la pantalla muestra.
        assertThat(columnas.get(1)).isEqualTo(cedula.substring(cedula.length() - 4));
        assertThat(columnas.get(3)).isEqualTo("3456");
    }

    /**
     * Dos filas con el mismo documento tienen que dar la misma huella, o el criterio 5
     * no podria compararlas; y el cifrado tiene que ser distinto, o revelaria que
     * comparten valor sin descifrar nada.
     */
    @Test
    void deberia_guardar_la_misma_huella_y_un_cifrado_distinto_para_el_mismo_documento() {
        UserId primera = cuentaNueva();
        UserId segunda = cuentaNueva();
        String cedula = cedulaNueva();

        verificaciones.guardar(completaDe(primera, cedula));
        verificaciones.guardar(completaDe(segunda, cedula));

        List<String> huellas = jdbc.sql("""
                        SELECT encode(document_number_lookup, 'hex') AS huella
                        FROM seller_verifications WHERE user_id IN (:primera, :segunda)
                        """)
                .param("primera", primera.value())
                .param("segunda", segunda.value())
                .query(String.class)
                .list();

        List<String> cifrados = jdbc.sql("""
                        SELECT document_number_cipher
                        FROM seller_verifications WHERE user_id IN (:primera, :segunda)
                        """)
                .param("primera", primera.value())
                .param("segunda", segunda.value())
                .query(String.class)
                .list();

        assertThat(huellas).hasSize(2);
        assertThat(huellas.get(0)).isEqualTo(huellas.get(1));
        assertThat(cifrados.get(0)).isNotEqualTo(cifrados.get(1));
    }

    // --- Criterio 5 y RN-010 -------------------------------------------------

    /**
     * La unicidad la garantiza el indice y no una consulta previa. Bajo concurrencia,
     * dos transacciones pueden comprobar «no existe» a la vez y las dos creer que
     * pueden aprobar; la segunda choca aqui.
     */
    @Test
    void deberia_cumplir_el_criterio_5_impidiendo_dos_verificados_con_el_mismo_documento() {
        UserId primera = cuentaNueva();
        UserId segunda = cuentaNueva();
        String cedula = cedulaNueva();
        Instant ahora = reloj.instant();

        verificaciones.guardar(
                completaDe(primera, cedula).enviarARevision(ahora).aprobar(ahora));

        SellerVerification tambienAprobada =
                completaDe(segunda, cedula).enviarARevision(ahora).aprobar(ahora);

        assertThatThrownBy(() -> verificaciones.guardar(tambienAprobada))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Dos en revision si pueden convivir: pasa cuando alguien intenta usar la cedula
     * de otro, y quien lo resuelve es el moderador. Lo que el criterio 5 prohibe es
     * que las dos queden verificadas.
     */
    @Test
    void deberia_permitir_dos_solicitudes_en_revision_con_el_mismo_documento() {
        UserId primera = cuentaNueva();
        UserId segunda = cuentaNueva();
        String cedula = cedulaNueva();
        Instant ahora = reloj.instant();

        verificaciones.guardar(completaDe(primera, cedula).enviarARevision(ahora));
        verificaciones.guardar(completaDe(segunda, cedula).enviarARevision(ahora));

        assertThat(verificaciones.buscarPorUsuario(segunda).orElseThrow().status())
                .isEqualTo(VerificationStatus.PENDING_REVIEW);
    }

    @Test
    void deberia_encontrar_el_documento_ya_verificado_en_otra_cuenta() {
        UserId dueno = cuentaNueva();
        UserId otra = cuentaNueva();
        String cedula = cedulaNueva();
        Instant ahora = reloj.instant();

        verificaciones.guardar(completaDe(dueno, cedula).enviarARevision(ahora).aprobar(ahora));

        assertThat(verificaciones.existeOtraVerificadaConDocumento(cedula, otra))
                .isTrue();
    }

    /** Quien corrige su propia solicitud no puede chocar consigo mismo. */
    @Test
    void deberia_excluir_la_propia_cuenta_al_buscar_el_documento() {
        UserId dueno = cuentaNueva();
        String cedula = cedulaNueva();
        Instant ahora = reloj.instant();

        verificaciones.guardar(completaDe(dueno, cedula).enviarARevision(ahora).aprobar(ahora));

        assertThat(verificaciones.existeOtraVerificadaConDocumento(cedula, dueno))
                .isFalse();
    }

    @Test
    void deberia_no_encontrar_un_documento_que_solo_esta_en_revision() {
        UserId enRevision = cuentaNueva();
        UserId otra = cuentaNueva();
        String cedula = cedulaNueva();

        verificaciones.guardar(completaDe(enRevision, cedula).enviarARevision(reloj.instant()));

        assertThat(verificaciones.existeOtraVerificadaConDocumento(cedula, otra))
                .isFalse();
    }

    // --- Estado y motivos -----------------------------------------------------

    @Test
    void deberia_guardar_el_motivo_y_la_nota_del_rechazo() {
        UserId usuario = cuentaNueva();
        String cedula = cedulaNueva();
        Instant ahora = reloj.instant();

        verificaciones.guardar(completaDe(usuario, cedula)
                .enviarARevision(ahora)
                .rechazar(RejectionReason.ILLEGIBLE_PHOTOS, "El reverso sale oscuro", ahora));

        SellerVerification leida = verificaciones.buscarPorUsuario(usuario).orElseThrow();

        assertThat(leida.status()).isEqualTo(VerificationStatus.REJECTED);
        assertThat(leida.rejectionReason()).isEqualTo(RejectionReason.ILLEGIBLE_PHOTOS);
        assertThat(leida.rejectionNote()).isEqualTo("El reverso sale oscuro");
        assertThat(leida.attempts()).isEqualTo(1);
    }

    /** La entidad tiene que existir en el catalogo de V7: la clave ajena lo exige. */
    @Test
    void deberia_rechazar_una_entidad_que_no_esta_en_el_catalogo() {
        UserId usuario = cuentaNueva();
        Instant ahora = reloj.instant();

        SellerVerification conBancoInventado = SellerVerification.iniciar(SellerVerificationId.nuevo(), usuario, ahora)
                .conCuentaBancaria(
                        new BankAccount(
                                new BankCode("banco-que-no-existe"),
                                BankAccountType.SAVINGS,
                                new BankAccountNumber(CUENTA),
                                TITULAR),
                        ahora);

        assertThatThrownBy(() -> verificaciones.guardar(conBancoInventado))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deberia_traer_el_catalogo_de_entidades_sembrado_en_V7() {
        Integer entidades = jdbc.sql("SELECT count(*) FROM financial_institutions WHERE active")
                .query(Integer.class)
                .single();

        Integer billeteras = jdbc.sql("SELECT count(*) FROM financial_institutions WHERE kind = 'WALLET'")
                .query(Integer.class)
                .single();

        assertThat(entidades).isEqualTo(28);
        assertThat(billeteras).isEqualTo(7);
    }
}
