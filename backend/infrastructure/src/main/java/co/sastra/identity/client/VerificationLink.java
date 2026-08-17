package co.sastra.identity.client;

import co.sastra.identity.config.MailProperties;
import co.sastra.shared.config.AppProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/**
 * Construye el enlace que activa una cuenta.
 *
 * <p>Vive en infraestructura porque necesita la direccion publica del sitio, que
 * es configuracion. Por eso el puerto {@code MailSender} recibe el token y no el
 * enlace ya montado: {@code application} no tiene por que saber de URL.
 */
@Component
public class VerificationLink {

    private final AppProperties app;
    private final MailProperties mail;

    public VerificationLink(AppProperties app, MailProperties mail) {
        this.app = app;
        this.mail = mail;
    }

    public String para(String tokenEnClaro) {
        String base = app.baseUrl().toString();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }

        // El token va codificado aunque se genere en base64 apto para URL: si
        // manana cambia el alfabeto del generador, el enlace sigue siendo valido.
        return base + mail.verificationPath() + "?token=" + URLEncoder.encode(tokenEnClaro, StandardCharsets.UTF_8);
    }
}
