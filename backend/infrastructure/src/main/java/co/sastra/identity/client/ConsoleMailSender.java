package co.sastra.identity.client;

import co.sastra.identity.model.User;
import co.sastra.identity.port.out.MailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adaptador de desarrollo: imprime el enlace en lugar de enviar correo.
 *
 * <p>Es lo que permite recorrer el registro y la verificacion completos sin
 * credenciales de ningun proveedor, que es justo lo que hace falta mientras el
 * dominio no esta comprado (ADR-0012).
 *
 * <p>Imprime el enlace entero a proposito, con el token en claro. Eso seria
 * inaceptable en produccion y por eso esta clase solo existe cuando
 * {@code sastra.mail.provider} vale {@code console}, algo que los perfiles
 * {@code dev} y {@code prod} no hacen.
 */
@Component
@ConditionalOnProperty(prefix = "sastra.mail", name = "provider", havingValue = "console")
public class ConsoleMailSender implements MailSender {

    private static final Logger LOG = LoggerFactory.getLogger(ConsoleMailSender.class);

    private final VerificationLink enlaces;

    public ConsoleMailSender(VerificationLink enlaces) {
        this.enlaces = enlaces;
    }

    @Override
    public void enviarVerificacionDeCorreo(User destinatario, String tokenEnClaro) {
        LOG.info("""

                ================ CORREO DE VERIFICACION (adaptador de consola) ================
                Para:   {}
                Enlace: {}
                ===============================================================================
                """, destinatario.email().value(), enlaces.para(tokenEnClaro));
    }

    @Override
    public void enviarAvisoDeRegistroConCorreoExistente(User titular) {
        LOG.info("""

                ================ AVISO DE REGISTRO CON CORREO EXISTENTE =======================
                Para: {}
                Alguien intento registrarse con este correo. No se creo ninguna cuenta nueva.
                ===============================================================================
                """, titular.email().value());
    }
}
