package co.sendik;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.sendik.identity.model.BankAccount;
import co.sendik.identity.model.BankAccountNumber;
import co.sendik.identity.model.BankAccountType;
import co.sendik.identity.model.BankCode;
import co.sendik.identity.model.BirthDate;
import co.sendik.identity.model.DisplayName;
import co.sendik.identity.model.Email;
import co.sendik.identity.model.IdentityDocument;
import co.sendik.identity.model.IdentityDocumentNumber;
import co.sendik.identity.model.IdentityDocumentType;
import co.sendik.identity.model.LegalName;
import co.sendik.identity.model.RawPassword;
import co.sendik.identity.model.RejectionReason;
import co.sendik.identity.model.Role;
import co.sendik.identity.model.SellerVerification;
import co.sendik.identity.model.SellerVerificationId;
import co.sendik.identity.model.User;
import co.sendik.identity.model.UserId;
import co.sendik.identity.model.UserLocale;
import co.sendik.identity.model.VerificationAccess;
import co.sendik.identity.model.VerificationStatus;
import co.sendik.identity.port.out.PasswordHasher;
import co.sendik.identity.port.out.SellerVerificationRepository;
import co.sendik.identity.port.out.UserRepository;
import co.sendik.identity.port.out.VerificationAccessLog;
import co.sendik.shared.file.FileKey;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
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
    private final VerificationAccessLog bitacora;
    private final UserRepository usuarios;
    private final PasswordHasher hasher;
    private final JdbcClient jdbc;
    private final Clock reloj;

    SellerVerificationPersistenceTest(
            SellerVerificationRepository verificaciones,
            VerificationAccessLog bitacora,
            UserRepository usuarios,
            PasswordHasher hasher,
            JdbcClient jdbc,
            Clock reloj) {
        this.verificaciones = verificaciones;
        this.bitacora = bitacora;
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

    // --- La cola del moderador -----------------------------------------------

    /**
     * <strong>La cola no tenia ninguna prueba de persistencia.</strong> Se podia quitar el
     * desplazamiento entero -y de hecho no existia- sin que nada de esta suite se pusiera
     * en rojo.
     *
     * <p>Se comprueba recorriendo, y no pidiendo una pagina concreta: el contenedor es uno
     * para toda la clase y otras pruebas dejan solicitudes en revision, asi que ni el total
     * ni la posicion de estas tres se pueden dar por sabidos. Lo que si se puede afirmar es
     * que recorriendo la cola de una en una aparecen las tres, cada una exactamente una vez.
     */
    @Test
    void deberia_llegar_a_las_pendientes_que_no_caben_en_la_primera_pagina() {
        Instant ahora = reloj.instant();
        List<SellerVerificationId> mias = new ArrayList<>();

        for (int cuantas = 0; cuantas < 3; cuantas++) {
            SellerVerification enviada =
                    completaDe(cuentaNueva(), cedulaNueva()).enviarARevision(ahora);
            verificaciones.guardar(enviada);
            mias.add(enviada.id());
        }

        List<SellerVerificationId> recorrida = new ArrayList<>();
        for (int pagina = 0; ; pagina++) {
            List<SellerVerification> hoja = verificaciones.pendientesDeRevision(pagina, 1);
            if (hoja.isEmpty()) {
                break;
            }
            hoja.forEach(pendiente -> recorrida.add(pendiente.id()));
        }

        assertThat(recorrida).containsAll(mias).doesNotHaveDuplicates();
    }

    /**
     * El desempate por {@code id}, que es lo que hace correcto el desplazamiento.
     *
     * <p>Las tres se envian en el <strong>mismo instante</strong>, que es justo el caso que
     * {@code ORDER BY updated_at} sola no ordena. Sin el desempate, dos peticiones de la
     * misma pagina pueden devolver filas distintas, y entre paginas eso se traduce en filas
     * repetidas y filas que no salen nunca.
     */
    @Test
    void deberia_ordenar_igual_dos_veces_aunque_el_instante_coincida() {
        Instant ahora = reloj.instant();

        for (int cuantas = 0; cuantas < 3; cuantas++) {
            verificaciones.guardar(completaDe(cuentaNueva(), cedulaNueva()).enviarARevision(ahora));
        }

        List<SellerVerificationId> primera = idsDeLaCola(0, 20);
        List<SellerVerificationId> otraVez = idsDeLaCola(0, 20);

        assertThat(primera).isEqualTo(otraVez);
    }

    private List<SellerVerificationId> idsDeLaCola(int pagina, int tamano) {
        return verificaciones.pendientesDeRevision(pagina, tamano).stream()
                .map(SellerVerification::id)
                .toList();
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

    // --- Roles y bitacora. Rebanada C2 ---------------------------------------

    /** Criterio 8: aprobar convierte a la persona en vendedora. */
    @Test
    void deberia_otorgar_el_rol_de_vendedor() {
        UserId usuario = cuentaNueva();

        usuarios.otorgarRol(usuario, Role.SELLER, reloj.instant());

        assertThat(rolesDe(usuario)).contains("SELLER");
    }

    /** Aprobar dos veces la misma solicitud no puede reventar por la clave primaria. */
    @Test
    void deberia_poder_otorgar_el_mismo_rol_dos_veces() {
        UserId usuario = cuentaNueva();

        usuarios.otorgarRol(usuario, Role.SELLER, reloj.instant());
        usuarios.otorgarRol(usuario, Role.SELLER, reloj.instant());

        assertThat(rolesDe(usuario)).containsOnlyOnce("SELLER");
    }

    /** RN-013: revocar quita el sello y deja el resto de los roles en su sitio. */
    @Test
    void deberia_cumplir_RN_013_quitando_solo_el_rol_de_vendedor() {
        UserId usuario = cuentaNueva();
        usuarios.otorgarRol(usuario, Role.SELLER, reloj.instant());

        usuarios.revocarRol(usuario, Role.SELLER);

        assertThat(rolesDe(usuario)).doesNotContain("SELLER").contains("BUYER");
    }

    @Test
    void deberia_poder_revocar_un_rol_que_no_tiene() {
        UserId usuario = cuentaNueva();

        usuarios.revocarRol(usuario, Role.SELLER);

        assertThat(rolesDe(usuario)).contains("BUYER");
    }

    @Test
    void deberia_anotar_en_la_bitacora_quien_vio_un_documento() {
        UserId vendedor = cuentaNueva();
        UserId moderador = cuentaNueva();
        SellerVerification solicitud = completaDe(vendedor, cedulaNueva());
        verificaciones.guardar(solicitud);

        bitacora.registrar(
                solicitud.id(),
                moderador,
                VerificationAccess.VIEW_DOCUMENT_FRONT,
                "revision de la solicitud",
                reloj.instant());

        List<String> anotado = jdbc.sql(
                        "SELECT action, reason FROM verification_access_log WHERE verification_id = :id")
                .param("id", solicitud.id().value())
                .query((fila, numero) -> List.of(fila.getString(1), fila.getString(2)))
                .single();

        assertThat(anotado).containsExactly("VIEW_DOCUMENT_FRONT", "revision de la solicitud");
    }

    /**
     * La accion se valida contra la lista del CHECK de V8. Una accion inventada en el
     * enum sin migracion que la agregue tiene que fallar aqui y no colarse.
     */
    @Test
    void deberia_rechazar_una_accion_que_la_migracion_no_conoce() {
        UserId vendedor = cuentaNueva();
        SellerVerification solicitud = completaDe(vendedor, cedulaNueva());
        verificaciones.guardar(solicitud);

        assertThatThrownBy(() -> jdbc.sql("""
                                INSERT INTO verification_access_log (id, verification_id, actor_id, action, created_at)
                                VALUES (:id, :verificacion, :actor, 'ESPIAR', now())
                                """)
                        .param("id", java.util.UUID.randomUUID())
                        .param("verificacion", solicitud.id().value())
                        .param("actor", vendedor.value())
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private List<String> rolesDe(UserId usuario) {
        return jdbc.sql("SELECT role FROM user_roles WHERE user_id = :usuario")
                .param("usuario", usuario.value())
                .query(String.class)
                .list();
    }
}
