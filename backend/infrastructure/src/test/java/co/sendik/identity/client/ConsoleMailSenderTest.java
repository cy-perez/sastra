package co.sendik.identity.client;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import co.sendik.identity.config.MailProperties;
import co.sendik.identity.model.BirthDate;
import co.sendik.identity.model.DisplayName;
import co.sendik.identity.model.Email;
import co.sendik.identity.model.User;
import co.sendik.identity.model.UserId;
import co.sendik.identity.model.UserLocale;
import co.sendik.shared.config.AppProperties;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * El adaptador de desarrollo que imprime el enlace en lugar de enviar correo
 * (ADR-0012).
 *
 * <p>Parece el menos importante de los dos y es el que sostiene dos cosas: recorrer
 * el registro completo sin credenciales de ningun proveedor, y las pruebas de
 * extremo a extremo de la Fase 1, que recuperan el token leyendo justamente estas
 * lineas del registro. Si el formato cambia, la suite completa de cuentas deja de
 * poder verificar un correo, asi que el formato es contrato y aqui se fija.
 */
class ConsoleMailSenderTest {

    private static final Instant AHORA = Instant.parse("2026-08-20T15:00:00Z");

    private ListAppender<ILoggingEvent> registro;
    private Logger logger;

    @BeforeEach
    void engancharElRegistro() {
        registro = new ListAppender<>();
        registro.start();
        logger = (Logger) LoggerFactory.getLogger(ConsoleMailSender.class);
        logger.addAppender(registro);
    }

    @AfterEach
    void desengancharElRegistro() {
        logger.detachAppender(registro);
    }

    private ConsoleMailSender transporte() {
        AppProperties app = new AppProperties(
                URI.create("http://localhost:4200"),
                URI.create("http://localhost:8080/api/v1"),
                "soporte@localhost",
                List.of("http://localhost:4200"),
                ZoneId.of("America/Bogota"));

        MailProperties correo = new MailProperties(
                "no-responder@localhost",
                null,
                URI.create("https://api.resend.com/emails"),
                "/verificar-correo",
                "/restablecer-contrasena",
                "/confirmar-correo-nuevo");

        return new ConsoleMailSender(new VerificationLink(app, correo));
    }

    private String loImpreso() {
        return registro.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (uno, otro) -> uno + "\n" + otro);
    }

    private User cuenta() {
        return User.registrar(
                UserId.nuevo(),
                new Email("ana@correo.co"),
                new DisplayName("Ana Maria"),
                new BirthDate(LocalDate.of(1990, 3, 4)),
                UserLocale.ES,
                LocalDate.of(2026, 8, 20),
                AHORA);
    }

    /**
     * El enlace entero, con el token en claro. Es inaceptable en produccion y por eso
     * la clase solo existe con {@code sendik.mail.provider=console}, que ni dev ni
     * prod usan.
     */
    @Test
    void deberia_imprimir_el_enlace_de_verificacion_con_el_token() {
        transporte().enviarVerificacionDeCorreo(cuenta(), "token-de-verificacion");

        assertThat(loImpreso())
                .contains("ana@correo.co")
                .contains("http://localhost:4200/verificar-correo?token=token-de-verificacion");
    }

    @Test
    void deberia_imprimir_el_enlace_de_restablecimiento_con_el_token() {
        transporte().enviarRestablecimientoDeContrasena(cuenta(), "token-de-reset");

        assertThat(loImpreso()).contains("http://localhost:4200/restablecer-contrasena?token=token-de-reset");
    }

    @Test
    void deberia_imprimir_el_enlace_de_cambio_de_correo_dirigido_al_correo_nuevo() {
        transporte().enviarConfirmacionDeCorreoNuevo(cuenta(), new Email("nuevo@correo.co"), "token-nuevo");

        assertThat(loImpreso())
                .contains("http://localhost:4200/confirmar-correo-nuevo?token=token-nuevo")
                .contains("nuevo@correo.co");
    }

    /**
     * Los avisos sin enlace tambien se imprimen: en local son la unica senal de que
     * el flujo llego hasta ahi.
     */
    @Test
    void deberia_imprimir_los_avisos_que_no_llevan_enlace() {
        ConsoleMailSender transporte = transporte();
        User titular = cuenta();

        transporte.enviarAvisoDeRegistroConCorreoExistente(titular);
        transporte.enviarAvisoDeCuentaBloqueada(titular, AHORA);
        transporte.enviarAvisoDeSesionRevocadaPorSeguridad(titular);
        transporte.enviarAvisoDeContrasenaCambiada(titular);
        transporte.enviarAvisoDeCuentaCerrada(titular);
        transporte.enviarAvisoDeIntentoDeCambioAEsteCorreo(titular);
        transporte.enviarAvisoDeCorreoCambiado(titular, new Email("viejo@correo.co"));

        assertThat(registro.list).hasSize(7);
        assertThat(loImpreso()).contains("viejo@correo.co");
    }
}
