package co.sastra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.sastra.identity.dto.ApproveVerificationCommand;
import co.sastra.identity.dto.RejectVerificationCommand;
import co.sastra.identity.dto.RevokeVerificationCommand;
import co.sastra.identity.dto.StartSellerVerificationCommand;
import co.sastra.identity.dto.SubmitBankAccountCommand;
import co.sastra.identity.dto.SubmitIdentityDocumentCommand;
import co.sastra.identity.dto.SubmitSelfieCommand;
import co.sastra.identity.dto.SubmitVerificationForReviewCommand;
import co.sastra.identity.exception.DocumentAlreadyVerifiedException;
import co.sastra.identity.exception.EmailNotVerifiedException;
import co.sastra.identity.exception.SelfReviewForbiddenException;
import co.sastra.identity.exception.VerificationAttemptsExhaustedException;
import co.sastra.identity.model.BankAccountType;
import co.sastra.identity.model.BirthDate;
import co.sastra.identity.model.DisplayName;
import co.sastra.identity.model.Email;
import co.sastra.identity.model.IdentityDocumentType;
import co.sastra.identity.model.RawPassword;
import co.sastra.identity.model.RejectionReason;
import co.sastra.identity.model.SellerVerification;
import co.sastra.identity.model.SellerVerificationId;
import co.sastra.identity.model.User;
import co.sastra.identity.model.UserId;
import co.sastra.identity.model.UserLocale;
import co.sastra.identity.model.VerificationStatus;
import co.sastra.identity.port.out.PasswordHasher;
import co.sastra.identity.port.out.UserRepository;
import co.sastra.identity.usecase.ApproveVerificationUseCase;
import co.sastra.identity.usecase.RejectVerificationUseCase;
import co.sastra.identity.usecase.RevokeVerificationUseCase;
import co.sastra.identity.usecase.StartSellerVerificationUseCase;
import co.sastra.identity.usecase.SubmitBankAccountUseCase;
import co.sastra.identity.usecase.SubmitIdentityDocumentUseCase;
import co.sastra.identity.usecase.SubmitSelfieUseCase;
import co.sastra.identity.usecase.SubmitVerificationForReviewUseCase;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * El recorrido de HU-002 de punta a punta contra PostgreSQL 17 real: iniciar, entregar
 * los tres datos, enviar a revision, y que un moderador decida hasta que el sello exista
 * o deje de existir.
 *
 * <p><strong>Esto es lo que la suite de navegador no puede cubrir.</strong> Aprobar exige
 * un moderador, y el rol se otorga con una sentencia SQL a mano porque no hay pantalla que
 * lo haga —decision anotada en HU-002—. Aqui esa sentencia se puede ejecutar; en
 * {@code e2e-completo} habria que agregarle al frontend un cliente de PostgreSQL. Asi que
 * el recorrido de la persona va por la interfaz alli, y la cadena hasta el sello va aqui,
 * donde hay base de datos.
 *
 * <p>Y no se solapa con {@code SellerVerificationPersistenceTest}: alli se comprueba que un
 * agregado sobreviva la ida y vuelta, construido a mano. Aqui no se construye nada a mano,
 * se llama a los mismos casos de uso que llama el borde, en el mismo orden, con el cifrado,
 * el almacen y la bitacora reales. Lo que se prueba es la cadena, que es lo unico donde se
 * ve si un caso de uso deja el sistema en un estado que el siguiente no acepta.
 */
@SpringBootTest(properties = "sastra.features.seller-verification=true")
@ActiveProfiles("local")
@Import(PostgresTestContainer.class)
class SellerVerificationJourneyTest {

    private static final String CONTRASENA = "una-contrasena-larga-de-prueba";

    private static final String CUENTA = "91500123456";

    private static final String TITULAR = "Ana Maria Garcia";

    /**
     * Documento distinto en cada prueba. El contenedor es uno para toda la clase y el
     * indice unico del criterio 5 es de toda la tabla: con una cedula constante, la primera
     * prueba que aprueba deja el documento tomado y las demas fallan por culpa de la
     * anterior. La secuencia arranca lejos de la de las otras clases para no chocar con
     * ellas.
     */
    private static final AtomicLong SECUENCIA = new AtomicLong(5_000_000);

    private final StartSellerVerificationUseCase iniciar;
    private final SubmitIdentityDocumentUseCase documento;
    private final SubmitSelfieUseCase selfie;
    private final SubmitBankAccountUseCase cuenta;
    private final SubmitVerificationForReviewUseCase enviar;
    private final ApproveVerificationUseCase aprobar;
    private final RejectVerificationUseCase rechazar;
    private final RevokeVerificationUseCase revocar;
    private final UserRepository usuarios;
    private final PasswordHasher hasher;
    private final JdbcClient jdbc;
    private final Clock reloj;

    SellerVerificationJourneyTest(
            StartSellerVerificationUseCase iniciar,
            SubmitIdentityDocumentUseCase documento,
            SubmitSelfieUseCase selfie,
            SubmitBankAccountUseCase cuenta,
            SubmitVerificationForReviewUseCase enviar,
            ApproveVerificationUseCase aprobar,
            RejectVerificationUseCase rechazar,
            RevokeVerificationUseCase revocar,
            UserRepository usuarios,
            PasswordHasher hasher,
            JdbcClient jdbc,
            Clock reloj) {
        this.iniciar = iniciar;
        this.documento = documento;
        this.selfie = selfie;
        this.cuenta = cuenta;
        this.enviar = enviar;
        this.aprobar = aprobar;
        this.rechazar = rechazar;
        this.revocar = revocar;
        this.usuarios = usuarios;
        this.hasher = hasher;
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    private static String cedulaNueva() {
        return "10" + SECUENCIA.incrementAndGet();
    }

    /**
     * Un PNG de verdad, no unos bytes con la firma correcta al principio: el normalizador
     * decodifica y vuelve a codificar, asi que un archivo falso no pasaria de ahi.
     */
    private static byte[] pngDe(int ancho, int alto) {
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                imagen.setRGB(x, y, (x + y) % 2 == 0 ? 0xFFFFFF : 0x000000);
            }
        }

        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            ImageIO.write(imagen, "png", salida);
            return salida.toByteArray();
        } catch (IOException fallo) {
            throw new UncheckedIOException(fallo);
        }
    }

    private User registrada() {
        return User.registrar(
                UserId.nuevo(),
                new Email("ana-" + UUID.randomUUID() + "@correo.co"),
                new DisplayName("Ana Maria"),
                new BirthDate(LocalDate.of(1990, 3, 4)),
                UserLocale.ES,
                LocalDate.now(reloj),
                reloj.instant());
    }

    /** Una cuenta con el correo ya verificado, que es lo que exige el criterio 1. */
    private UserId cuentaVerificada() {
        User nueva = registrada();

        usuarios.crear(nueva, hasher.hashear(new RawPassword(CONTRASENA)));
        usuarios.actualizar(nueva.conCorreoVerificado(reloj.instant()));

        return nueva.id();
    }

    /**
     * El rol de moderador, con la misma sentencia que documenta HU-002. Se escribe aqui tal
     * cual, y no por un metodo del repositorio, porque es la unica forma que hay de
     * otorgarlo: si esa sentencia deja de servir, esta prueba tiene que enterarse.
     */
    private UserId moderador() {
        UserId quien = cuentaVerificada();

        jdbc.sql("""
                        INSERT INTO user_roles (user_id, role, granted_at)
                        VALUES (:usuario, 'MODERATOR', now())
                        """).param("usuario", quien.value()).update();

        return quien;
    }

    /** Los tres datos, por los mismos casos de uso que usa el borde HTTP. */
    private SellerVerification solicitudCompleta(UserId vendedor, String cedula) {
        iniciar.execute(new StartSellerVerificationCommand(vendedor));

        documento.execute(new SubmitIdentityDocumentCommand(
                vendedor, IdentityDocumentType.CC, cedula, TITULAR, pngDe(300, 200), pngDe(320, 220)));

        selfie.execute(new SubmitSelfieCommand(vendedor, pngDe(300, 300)));

        return cuenta.execute(
                new SubmitBankAccountCommand(vendedor, "bancolombia", BankAccountType.SAVINGS, CUENTA, TITULAR));
    }

    private List<String> rolesDe(UserId usuario) {
        return jdbc.sql("SELECT role FROM user_roles WHERE user_id = :usuario")
                .param("usuario", usuario.value())
                .query(String.class)
                .list();
    }

    private List<String> bitacoraDe(SellerVerificationId verificacion) {
        return jdbc.sql("""
                        SELECT action FROM verification_access_log
                        WHERE verification_id = :verificacion
                        ORDER BY created_at, action
                        """)
                .param("verificacion", verificacion.value())
                .query(String.class)
                .list();
    }

    // --- El camino principal --------------------------------------------------

    /**
     * Va en una sola prueba porque cada paso necesita el estado que dejo el anterior, y
     * trocearlo obligaria a fabricar ese estado a mano, que es justo lo que esta prueba
     * existe para no hacer.
     */
    @Test
    void deberia_recorrer_la_verificacion_hasta_obtener_el_sello() {
        UserId vendedor = cuentaVerificada();
        UserId quienRevisa = moderador();

        SellerVerification completa = solicitudCompleta(vendedor, cedulaNueva());

        assertThat(completa.estaCompleta()).isTrue();
        // Todavia no: el sello lo da la aprobacion, no el haber llenado el formulario.
        assertThat(rolesDe(vendedor)).doesNotContain("SELLER");

        SellerVerification enRevision = enviar.execute(new SubmitVerificationForReviewCommand(vendedor));

        assertThat(enRevision.status()).isEqualTo(VerificationStatus.PENDING_REVIEW);
        assertThat(enRevision.attempts()).isEqualTo(1);

        SellerVerification aprobada = aprobar.execute(new ApproveVerificationCommand(quienRevisa, enRevision.id()));

        // Criterio 8: el sello y el rol, que son dos cosas y tienen que ir juntas.
        assertThat(aprobada.status().esVerificado()).isTrue();
        assertThat(rolesDe(vendedor)).contains("SELLER");

        // Y la decision quedo anotada con su actor (RN-046).
        assertThat(bitacoraDe(enRevision.id())).containsExactly("APPROVE");
    }

    // --- Los caminos que no llegan al sello ----------------------------------

    /** Criterio 1: sin correo verificado no se empieza, ni con todo lo demas en orden. */
    @Test
    void deberia_cumplir_el_criterio_1_sin_correo_verificado() {
        User sinVerificar = registrada();
        usuarios.crear(sinVerificar, hasher.hashear(new RawPassword(CONTRASENA)));

        assertThatThrownBy(() -> iniciar.execute(new StartSellerVerificationCommand(sinVerificar.id())))
                .isInstanceOf(EmailNotVerifiedException.class);
    }

    /**
     * Criterio 5 y RN-010 recorridos enteros: la primera persona queda verificada y la
     * segunda no puede ni entregar el documento.
     *
     * <p>La prueba de persistencia ya demuestra que el indice unico existe. Lo que se
     * demuestra aqui es lo otro: que quien llega segundo reciba una negativa de negocio y
     * no una violacion de integridad, que es la diferencia entre un mensaje y una pantalla
     * de error.
     */
    @Test
    void deberia_cumplir_el_criterio_5_con_dos_personas_y_el_mismo_documento() {
        String cedula = cedulaNueva();
        UserId quienRevisa = moderador();

        UserId primera = cuentaVerificada();
        solicitudCompleta(primera, cedula);
        aprobar.execute(new ApproveVerificationCommand(
                quienRevisa,
                enviar.execute(new SubmitVerificationForReviewCommand(primera)).id()));

        UserId segunda = cuentaVerificada();
        iniciar.execute(new StartSellerVerificationCommand(segunda));

        assertThatThrownBy(() -> documento.execute(new SubmitIdentityDocumentCommand(
                        segunda, IdentityDocumentType.CE, cedula, TITULAR, pngDe(300, 200), pngDe(320, 220))))
                .isInstanceOf(DocumentAlreadyVerifiedException.class);

        assertThat(rolesDe(segunda)).doesNotContain("SELLER");
    }

    /**
     * RN-014 recorrida entera: tres envios rechazados, y al cuarto ya no se puede ni
     * empezar a corregir.
     *
     * <p>Entre un rechazo y el envio siguiente hay que tocar algo, porque RN-059 no admite
     * ir de {@code REJECTED} a {@code PENDING_REVIEW} sin pasar por {@code IN_PROGRESS}.
     * Eso no es un detalle del recorrido: es lo que evita reenviar un rechazo identico, sin
     * cambiar nada, hasta que a alguien se le escape.
     */
    @Test
    void deberia_cumplir_RN_014_agotando_los_tres_intentos() {
        UserId vendedor = cuentaVerificada();
        UserId quienRevisa = moderador();

        SellerVerificationId solicitud =
                solicitudCompleta(vendedor, cedulaNueva()).id();

        for (int intento = 1; intento <= SellerVerification.MAXIMO_INTENTOS; intento++) {
            if (intento > 1) {
                // Corrige la foto que le dijeron que salia mal.
                selfie.execute(new SubmitSelfieCommand(vendedor, pngDe(300, 300)));
            }

            SellerVerification enRevision = enviar.execute(new SubmitVerificationForReviewCommand(vendedor));
            assertThat(enRevision.attempts()).isEqualTo(intento);

            rechazar.execute(new RejectVerificationCommand(
                    quienRevisa, solicitud, RejectionReason.ILLEGIBLE_PHOTOS, "El reverso sale oscuro"));
        }

        // El cuarto se niega al tocar el formulario, no al enviarlo: corregirlo todo para
        // recibir la negativa al final es la misma negativa con el trabajo perdido en medio.
        assertThatThrownBy(() -> selfie.execute(new SubmitSelfieCommand(vendedor, pngDe(300, 300))))
                .isInstanceOf(VerificationAttemptsExhaustedException.class);

        assertThat(rolesDe(vendedor)).doesNotContain("SELLER");
        assertThat(bitacoraDe(solicitud)).containsExactly("REJECT", "REJECT", "REJECT");
    }

    /** RN-013 recorrida entera: se otorga el sello y despues se quita. */
    @Test
    void deberia_cumplir_RN_013_revocando_el_sello_ya_otorgado() {
        UserId vendedor = cuentaVerificada();
        UserId quienRevisa = moderador();

        solicitudCompleta(vendedor, cedulaNueva());
        SellerVerificationId solicitud =
                enviar.execute(new SubmitVerificationForReviewCommand(vendedor)).id();
        aprobar.execute(new ApproveVerificationCommand(quienRevisa, solicitud));

        assertThat(rolesDe(vendedor)).contains("SELLER");

        SellerVerification revocada = revocar.execute(
                new RevokeVerificationCommand(quienRevisa, solicitud, RejectionReason.REQUIREMENTS_NOT_MET, null));

        assertThat(revocada.status()).isEqualTo(VerificationStatus.REVOKED);
        // Deja de poder publicar y sigue siendo compradora: el rol se quita, la cuenta no.
        assertThat(rolesDe(vendedor)).doesNotContain("SELLER").contains("BUYER");
        assertThat(bitacoraDe(solicitud)).containsExactly("APPROVE", "REVOKE");
    }

    /**
     * RN-060 sobre la base de verdad, con las dos caras en la misma prueba: la propia se
     * rechaza y la ajena se aprueba.
     *
     * <p>Las dos juntas a proposito. Solo con la primera, un `if` invertido —o una regla
     * que bloqueara a todo el mundo— pasaria igual y dejaria la bandeja inservible sin
     * que ninguna prueba lo dijera.
     *
     * <p>El moderador se verifica como vendedor, que RN-010 permite: lo que no puede es
     * ser quien decida.
     */
    @Test
    void deberia_cumplir_RN_060_dejando_decidir_solo_sobre_solicitudes_ajenas() {
        UserId quienRevisa = moderador();

        // Su propia solicitud, enviada por el mismo camino que cualquiera.
        solicitudCompleta(quienRevisa, cedulaNueva());
        SellerVerificationId propia = enviar.execute(new SubmitVerificationForReviewCommand(quienRevisa))
                .id();

        assertThatThrownBy(() -> aprobar.execute(new ApproveVerificationCommand(quienRevisa, propia)))
                .isInstanceOf(SelfReviewForbiddenException.class);
        assertThatThrownBy(() -> rechazar.execute(
                        new RejectVerificationCommand(quienRevisa, propia, RejectionReason.ILLEGIBLE_PHOTOS, null)))
                .isInstanceOf(SelfReviewForbiddenException.class);

        // Nada quedo escrito: ni el rol, ni la bitacora, ni el estado.
        assertThat(rolesDe(quienRevisa)).doesNotContain("SELLER");
        assertThat(bitacoraDe(propia)).isEmpty();

        // Y la de otra persona se aprueba con normalidad.
        UserId vendedor = cuentaVerificada();
        solicitudCompleta(vendedor, cedulaNueva());
        SellerVerificationId ajena =
                enviar.execute(new SubmitVerificationForReviewCommand(vendedor)).id();

        assertThat(aprobar.execute(new ApproveVerificationCommand(quienRevisa, ajena))
                        .status())
                .isEqualTo(VerificationStatus.VERIFIED);
        assertThat(rolesDe(vendedor)).contains("SELLER");
    }

    /**
     * Lo que quedo guardado de verdad, mirado con SQL despues del recorrido completo: ni la
     * cedula ni el numero de cuenta aparecen en claro en ninguna columna de la fila
     * (RN-046, ADR-0020).
     *
     * <p>Se mira aqui otra vez, y no solo en la prueba de persistencia, porque el recorrido
     * reescribe la fila varias veces por caminos distintos: basta que uno de ellos guarde
     * sin cifrar para perder el cifrado en el ultimo paso sin que nada mas lo note.
     */
    @Test
    void deberia_cumplir_RN_046_despues_del_recorrido_completo() {
        String cedula = cedulaNueva();
        UserId vendedor = cuentaVerificada();
        UserId quienRevisa = moderador();

        solicitudCompleta(vendedor, cedula);
        aprobar.execute(new ApproveVerificationCommand(
                quienRevisa,
                enviar.execute(new SubmitVerificationForReviewCommand(vendedor)).id()));

        String fila = jdbc.sql("SELECT v::text FROM seller_verifications v WHERE v.user_id = :usuario")
                .param("usuario", vendedor.value())
                .query(String.class)
                .single();

        assertThat(fila).doesNotContain(cedula).doesNotContain(CUENTA);
        // Y los cuatro ultimos si estan, porque son lo que la pantalla muestra.
        assertThat(fila).contains(cedula.substring(cedula.length() - 4));
    }
}
