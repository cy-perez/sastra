package co.sastra.identity.client;

import co.sastra.identity.config.MailProperties;
import co.sastra.identity.model.User;
import co.sastra.identity.model.UserLocale;
import co.sastra.identity.port.out.MailSender;
import java.time.Duration;
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
@Component
@ConditionalOnProperty(prefix = "sastra.mail", name = "provider", havingValue = "resend", matchIfMissing = true)
public class ResendMailSender implements MailSender {

    private static final Logger LOG = LoggerFactory.getLogger(ResendMailSender.class);
    private static final Duration TIEMPO_DE_ESPERA = Duration.ofSeconds(10);

    private final RestClient cliente;
    private final MailProperties propiedades;
    private final VerificationLink enlaces;

    public ResendMailSender(MailProperties propiedades, VerificationLink enlaces) {
        this.propiedades = propiedades;
        this.enlaces = enlaces;

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
        } catch (RuntimeException e) {
            // Sin el asunto ni el cuerpo: el registro no debe llevar el enlace de
            // verificacion, que es una credencial.
            LOG.error("No se pudo enviar un correo transaccional: {}", e.getMessage());
        }
    }
}
