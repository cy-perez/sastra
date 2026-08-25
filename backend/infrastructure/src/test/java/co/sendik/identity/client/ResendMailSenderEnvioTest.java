package co.sendik.identity.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import co.sendik.identity.config.MailProperties;
import co.sendik.identity.config.VerificationProperties;
import co.sendik.identity.model.BirthDate;
import co.sendik.identity.model.DisplayName;
import co.sendik.identity.model.Email;
import co.sendik.identity.model.Role;
import co.sendik.identity.model.User;
import co.sendik.identity.model.UserId;
import co.sendik.identity.model.UserLocale;
import co.sendik.identity.model.UserStatus;
import co.sendik.shared.config.AppProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * El envio real de los correos transaccionales (ADR-0012).
 *
 * <p>{@link ResendMailSenderTest} cubre la validacion de la clave al construirse.
 * Esto cubre lo otro: que el mensaje sale, que sale con lo que debe llevar dentro y
 * que un fallo del proveedor no se propaga.
 *
 * <p>Corre contra un servidor local, que es exactamente para lo que
 * {@code sendik.mail.api-url} es parametrizable —lo dice su propio Javadoc en
 * {@link MailProperties}—. Sin esto, los diez mensajes del sistema no tenian
 * ninguna prueba: el unico modo de descubrir que uno llevaba el enlace equivocado
 * o el asunto en el idioma equivocado era que alguien lo recibiera.
 */
class ResendMailSenderEnvioTest {

    private static final Instant AHORA = Instant.parse("2026-08-20T15:00:00Z");
    private static final String CLAVE = "re_una_clave_de_verdad";

    private HttpServer servidor;
    private final List<String> cuerposRecibidos = new CopyOnWriteArrayList<>();
    private final List<String> autorizaciones = new CopyOnWriteArrayList<>();

    private int codigo = 200;

    @BeforeEach
    void levantarServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/emails", intercambio -> {
            cuerposRecibidos.add(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String autorizacion = intercambio.getRequestHeaders().getFirst("Authorization");
            autorizaciones.add(autorizacion == null ? "" : autorizacion);

            byte[] respuesta = "{\"id\":\"1\"}".getBytes(StandardCharsets.UTF_8);
            intercambio.sendResponseHeaders(codigo, respuesta.length);
            try (OutputStream salida = intercambio.getResponseBody()) {
                salida.write(respuesta);
            }
        });
        servidor.start();
    }

    @AfterEach
    void apagarServidor() {
        servidor.stop(0);
    }

    private AppProperties app() {
        return new AppProperties(
                URI.create("https://sendik.co"),
                URI.create("https://sendik.co/api/v1"),
                "soporte@sendik.co",
                List.of("https://sendik.co"),
                ZoneId.of("America/Bogota"));
    }

    private ResendMailSender transporte() {
        MailProperties correo = new MailProperties(
                "no-responder@sendik.co",
                CLAVE,
                URI.create("http://127.0.0.1:" + servidor.getAddress().getPort() + "/emails"),
                "/verificar-correo",
                "/restablecer-contrasena",
                "/confirmar-correo-nuevo");

        return new ResendMailSender(correo, new VerificationLink(app(), correo), app(), new VerificationProperties(2));
    }

    private User cuenta(UserLocale idioma) {
        return User.rehidratar(
                UserId.nuevo(),
                new Email("ana@correo.co"),
                new DisplayName("Ana Maria"),
                new BirthDate(LocalDate.of(1990, 3, 4)),
                null,
                null,
                // Sin foto de perfil: esta prueba no trata de eso.
                null,
                idioma,
                UserStatus.ACTIVE,
                AHORA.minus(Duration.ofDays(1)),
                EnumSet.of(Role.BUYER),
                AHORA.minus(Duration.ofDays(30)));
    }

    private String unicoCuerpo() {
        assertThat(cuerposRecibidos).hasSize(1);
        return cuerposRecibidos.getFirst();
    }

    @Test
    void deberia_enviar_la_verificacion_con_su_enlace_al_destinatario() {
        transporte().enviarVerificacionDeCorreo(cuenta(UserLocale.ES), "token-de-verificacion");

        assertThat(unicoCuerpo())
                .contains("\"to\":[\"ana@correo.co\"]")
                .contains("\"from\":\"no-responder@sendik.co\"")
                .contains("https://sendik.co/verificar-correo?token=token-de-verificacion");
    }

    /** La clave viaja en la cabecera, nunca en el cuerpo ni en la direccion. */
    @Test
    void deberia_autenticarse_con_la_clave_en_la_cabecera() {
        transporte().enviarVerificacionDeCorreo(cuenta(UserLocale.ES), "token");

        assertThat(autorizaciones).containsExactly("Bearer " + CLAVE);
        assertThat(unicoCuerpo()).doesNotContain(CLAVE);
    }

    /**
     * El idioma sale de la cuenta, no del navegador de quien disparo la accion: el
     * correo de bloqueo lo provoca a veces un tercero intentando entrar, y quien lo
     * recibe es su titular.
     */
    @Test
    void deberia_escribir_en_el_idioma_de_la_cuenta() {
        transporte().enviarVerificacionDeCorreo(cuenta(UserLocale.ES), "token");
        assertThat(unicoCuerpo()).contains("Confirma tu correo en Sendik");

        cuerposRecibidos.clear();

        transporte().enviarVerificacionDeCorreo(cuenta(UserLocale.EN), "token");
        assertThat(unicoCuerpo()).contains("Confirm your email on Sendik");
    }

    /** Cada enlace a su pantalla: un token de restablecimiento no se canjea en la de verificacion. */
    @Test
    void deberia_mandar_cada_enlace_a_su_propia_pantalla() {
        ResendMailSender transporte = transporte();

        transporte.enviarRestablecimientoDeContrasena(cuenta(UserLocale.ES), "token-de-reset");
        assertThat(unicoCuerpo()).contains("/restablecer-contrasena?token=token-de-reset");

        cuerposRecibidos.clear();

        transporte.enviarConfirmacionDeCorreoNuevo(cuenta(UserLocale.ES), new Email("nuevo@correo.co"), "token-nuevo");
        assertThat(unicoCuerpo())
                .contains("/confirmar-correo-nuevo?token=token-nuevo")
                .contains("\"to\":[\"nuevo@correo.co\"]");
    }

    /**
     * La confirmacion del correo nuevo va al correo nuevo y el aviso de que cambio va
     * al anterior. Invertirlos deja a la persona sin poder confirmar y avisa al
     * buzon que ya no usa.
     */
    @Test
    void deberia_avisar_al_correo_anterior_de_que_la_cuenta_cambio() {
        transporte().enviarAvisoDeCorreoCambiado(cuenta(UserLocale.ES), new Email("viejo@correo.co"));

        assertThat(unicoCuerpo()).contains("\"to\":[\"viejo@correo.co\"]");
    }

    /** RN-006: el aviso de bloqueo dice a que hora se desbloquea, en la zona del proyecto. */
    @Test
    void deberia_decir_a_que_hora_se_desbloquea_la_cuenta() {
        transporte().enviarAvisoDeCuentaBloqueada(cuenta(UserLocale.ES), Instant.parse("2026-08-20T20:30:00Z"));

        // 20:30 UTC son 15:30 en America/Bogota.
        assertThat(unicoCuerpo()).contains("15:30");
    }

    /**
     * Los diez mensajes del sistema salen. Uno que no llegue a enviarse no rompe
     * nada visible: simplemente alguien no se entera de algo que le concierne.
     */
    @Test
    void deberia_enviar_los_diez_mensajes_del_sistema() {
        ResendMailSender transporte = transporte();
        User titular = cuenta(UserLocale.ES);

        transporte.enviarVerificacionDeCorreo(titular, "t1");
        transporte.enviarAvisoDeRegistroConCorreoExistente(titular);
        transporte.enviarAvisoDeCuentaBloqueada(titular, AHORA);
        transporte.enviarAvisoDeSesionRevocadaPorSeguridad(titular);
        transporte.enviarRestablecimientoDeContrasena(titular, "t2");
        transporte.enviarAvisoDeContrasenaCambiada(titular);
        transporte.enviarAvisoDeCuentaCerrada(titular);
        transporte.enviarConfirmacionDeCorreoNuevo(titular, new Email("nuevo@correo.co"), "t3");
        transporte.enviarAvisoDeIntentoDeCambioAEsteCorreo(titular);
        transporte.enviarAvisoDeCorreoCambiado(titular, new Email("viejo@correo.co"));

        assertThat(cuerposRecibidos).hasSize(10);
    }

    /**
     * Un error del proveedor no se propaga: se registra. Quien acaba de registrarse
     * ya tiene cuenta, y devolverle un error por un correo que no salio le diria que
     * su registro fallo cuando no fallo.
     */
    @Test
    void no_deberia_propagar_un_error_del_proveedor() {
        codigo = 422;

        assertThatCode(() -> transporte().enviarVerificacionDeCorreo(cuenta(UserLocale.ES), "token"))
                .doesNotThrowAnyException();
    }

    /** Lo mismo si el proveedor no esta: la direccion no responde y nadie se cae. */
    @Test
    void no_deberia_propagar_una_caida_del_proveedor() {
        ResendMailSender transporte = transporte();
        servidor.stop(0);

        assertThatCode(() -> transporte.enviarVerificacionDeCorreo(cuenta(UserLocale.ES), "token"))
                .doesNotThrowAnyException();
    }
}
