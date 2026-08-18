package co.sastra.identity.client;

import co.sastra.identity.config.MailProperties;
import co.sastra.identity.model.User;
import co.sastra.identity.model.UserLocale;
import co.sastra.identity.port.out.MailSender;
import co.sastra.shared.config.AppProperties;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Adaptador de correo transaccional con Resend (ADR-0012).
 *
 * <p>Con {@code RestClient} y sin el SDK del proveedor: son dos mensajes y un
 * POST, y una dependencia menos es una dependencia menos que actualizar.
 *
 * <p><strong>Ningun metodo lanza.</strong> Un correo que no sale no debe impedir
 * crear la cuenta: la persona siempre puede pedir el reenvio, y perder el
 * registro entero por una caida del proveedor es peor que llegar tarde.
 */
// Mismo nombre que ConsoleMailSender: solo uno de los dos esta activo, y
// AsyncMailSender pide "transporteDeCorreo" sin saber cual le toco.
@Component("transporteDeCorreo")
@ConditionalOnProperty(prefix = "sastra.mail", name = "provider", havingValue = "resend", matchIfMissing = true)
public class ResendMailSender implements MailSender {

    private static final Logger LOG = LoggerFactory.getLogger(ResendMailSender.class);
    private static final Duration TIEMPO_DE_ESPERA = Duration.ofSeconds(10);

    /**
     * Solo horas y minutos, sin nombre de zona ni formato regional: "15:42" se
     * entiende igual en los dos idiomas y no depende de la configuracion regional
     * del servidor.
     */
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final RestClient cliente;
    private final MailProperties propiedades;
    private final VerificationLink enlaces;

    /** Para dar la hora de desbloqueo en la zona de operacion y no en UTC. */
    private final ZoneId zona;

    public ResendMailSender(MailProperties propiedades, VerificationLink enlaces, AppProperties app) {
        this.propiedades = propiedades;
        this.enlaces = enlaces;
        this.zona = app.timeZone();

        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory();
        fabrica.setReadTimeout(TIEMPO_DE_ESPERA);

        this.cliente = RestClient.builder()
                .baseUrl(propiedades.apiUrl().toString())
                .requestFactory(fabrica)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + propiedades.providerApiKey())
                .build();
    }

    @Override
    public void enviarVerificacionDeCorreo(User destinatario, String tokenEnClaro) {
        boolean espanol = destinatario.locale() == UserLocale.ES;
        String enlace = enlaces.para(tokenEnClaro);

        enviar(
                destinatario.email().value(),
                espanol ? "Confirma tu correo en Sastra" : "Confirm your email on Sastra",
                espanol
                        ? cuerpo(
                                "Confirma tu correo",
                                "Toca el enlace para activar tu cuenta.",
                                enlace,
                                "Confirmar correo")
                        : cuerpo(
                                "Confirm your email",
                                "Tap the link to activate your account.",
                                enlace,
                                "Confirm email"));
    }

    @Override
    public void enviarAvisoDeRegistroConCorreoExistente(User titular) {
        boolean espanol = titular.locale() == UserLocale.ES;

        enviar(
                titular.email().value(),
                espanol ? "Alguien intento registrarse con tu correo" : "Someone tried to register with your email",
                espanol
                        ? "<p>Alguien intento crear una cuenta en Sastra con tu correo. "
                                + "No se creo ninguna cuenta nueva y tu sesion no cambio. "
                                + "Si fuiste tu, ya tienes cuenta: entra con tu contrasena.</p>"
                        : "<p>Someone tried to create a Sastra account with your email. "
                                + "No new account was created and your session did not change. "
                                + "If it was you, you already have an account: sign in instead.</p>");
    }

    @Override
    public void enviarAvisoDeCuentaBloqueada(User titular, Instant desbloqueoEn) {
        boolean espanol = titular.locale() == UserLocale.ES;
        String hora = HORA.format(desbloqueoEn.atZone(zona));

        enviar(
                titular.email().value(),
                espanol ? "Bloqueamos el acceso a tu cuenta" : "We locked access to your account",
                espanol
                        ? "<p>Hubo varios intentos fallidos de entrar a tu cuenta, asi que bloqueamos "
                                + "el acceso por seguridad. Puedes volver a intentarlo a partir de las " + hora
                                + ".</p><p>Si no fuiste tu, tu contrasena sigue siendo la misma y nadie entro. "
                                + "Cuando puedas, cambiala.</p>"
                        : "<p>There were several failed attempts to sign in to your account, so we locked "
                                + "access for safety. You can try again after " + hora
                                + ".</p><p>If this was not you, your password has not changed and nobody got in. "
                                + "Change it when you can.</p>");
    }

    @Override
    public void enviarAvisoDeSesionRevocadaPorSeguridad(User titular) {
        boolean espanol = titular.locale() == UserLocale.ES;

        enviar(
                titular.email().value(),
                espanol ? "Cerramos tus sesiones por seguridad" : "We closed your sessions for safety",
                espanol
                        ? "<p>Detectamos que se reutilizo una credencial de sesion antigua, que es senal "
                                + "de que alguien pudo haberla copiado. Cerramos esa sesion completa.</p>"
                                + "<p>Entra de nuevo con tu contrasena. Si no reconoces esto, cambiala.</p>"
                        : "<p>We detected an old session credential being reused, which can mean someone "
                                + "copied it. We closed that whole session.</p>"
                                + "<p>Sign in again with your password. If this looks wrong, change it.</p>");
    }

    /** Criterio 18: el enlace dura 30 minutos y se dice en el mensaje. */
    @Override
    public void enviarRestablecimientoDeContrasena(User destinatario, String tokenEnClaro) {
        boolean espanol = destinatario.locale() == UserLocale.ES;
        String enlace = enlaces.paraRestablecer(tokenEnClaro);

        enviar(
                destinatario.email().value(),
                espanol ? "Restablece tu contrasena en Sastra" : "Reset your Sastra password",
                espanol
                        ? cuerpo(
                                "Restablece tu contrasena",
                                "Pediste cambiar tu contrasena. El enlace sirve una sola vez y vence en 30 "
                                        + "minutos. Si no fuiste tu, ignora este mensaje: tu contrasena no cambia.",
                                enlace,
                                "Poner una contrasena nueva")
                        : cuerpo(
                                "Reset your password",
                                "You asked to change your password. The link works once and expires in 30 "
                                        + "minutes. If this was not you, ignore this message: your password stays "
                                        + "the same.",
                                enlace,
                                "Set a new password"));
    }

    /**
     * Criterio 20. Sin enlace y sin boton a proposito: es un aviso, y un correo de
     * "tu contrasena cambio" con un enlace dentro es exactamente la forma del
     * fraude que la persona deberia aprender a desconfiar.
     */
    @Override
    public void enviarAvisoDeContrasenaCambiada(User titular) {
        boolean espanol = titular.locale() == UserLocale.ES;

        enviar(
                titular.email().value(),
                espanol ? "Tu contrasena cambio" : "Your password changed",
                espanol
                        ? "<p>Tu contrasena de Sastra acaba de cambiar y cerramos todas las sesiones "
                                + "abiertas. Entra de nuevo con la contrasena nueva.</p>"
                                + "<p>Si no fuiste tu, alguien tiene acceso a este correo. Escribenos de "
                                + "inmediato desde la pagina de contacto.</p>"
                        : "<p>Your Sastra password has just changed and we closed every open session. "
                                + "Sign in again with the new password.</p>"
                                + "<p>If this was not you, someone has access to this mailbox. Contact us "
                                + "right away from the contact page.</p>");
    }

    /** Criterio 23. Sin enlace: es el ultimo mensaje y no hay nada que abrir. */
    @Override
    public void enviarAvisoDeCuentaCerrada(User titular) {
        boolean espanol = titular.locale() == UserLocale.ES;

        enviar(
                titular.email().value(),
                espanol ? "Tu cuenta de Sastra quedo cerrada" : "Your Sastra account is closed",
                espanol
                        ? "<p>Cerramos tu cuenta y borramos los datos que te identificaban. "
                                + "Este es el ultimo mensaje que te enviamos.</p>"
                                + "<p>Si quieres volver, puedes registrarte de nuevo con este mismo correo.</p>"
                                + "<p>Si no fuiste tu quien lo pidio, escribenos de inmediato.</p>"
                        : "<p>We closed your account and deleted the data that identified you. "
                                + "This is the last message we will send you.</p>"
                                + "<p>If you want to come back, you can register again with this same address.</p>"
                                + "<p>If you did not ask for this, contact us right away.</p>");
    }

    private static String cuerpo(String titulo, String texto, String enlace, String etiquetaDelBoton) {
        return "<h1>" + titulo + "</h1><p>" + texto + "</p><p><a href=\"" + enlace + "\">" + etiquetaDelBoton
                + "</a></p>";
    }

    private void enviar(String destinatario, String asunto, String html) {
        Map<String, Object> peticion =
                Map.of("from", propiedades.from(), "to", List.of(destinatario), "subject", asunto, "html", html);

        try {
            cliente.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(peticion)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            // Solo el codigo de estado. El mensaje de esta excepcion incluye parte
            // del cuerpo que devolvio el proveedor, y ese cuerpo puede repetir la
            // direccion de destino (docs/operacion/datos-personales.md).
            LOG.error(
                    "El proveedor rechazo un correo transaccional con estado {}",
                    e.getStatusCode().value());
        } catch (RuntimeException e) {
            // Sin el asunto, sin el cuerpo y sin el mensaje: el registro no debe
            // llevar el enlace de verificacion, que es una credencial, ni la
            // direccion de nadie.
            LOG.error(
                    "No se pudo enviar un correo transaccional: {}",
                    e.getClass().getName());
        }
    }
}
