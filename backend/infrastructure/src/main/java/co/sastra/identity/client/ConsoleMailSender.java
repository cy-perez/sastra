package co.sastra.identity.client;

import co.sastra.identity.model.User;
import co.sastra.identity.port.out.MailSender;
import java.time.Instant;
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
// El nombre lo comparte con ResendMailSender a proposito: solo uno de los dos
// esta activo, y AsyncMailSender pide "transporteDeCorreo" sin tener que saber
// cual de ellos le toco.
@Component("transporteDeCorreo")
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

    @Override
    public void enviarAvisoDeCuentaBloqueada(User titular, Instant desbloqueoEn) {
        LOG.info("""

                ================ AVISO DE CUENTA BLOQUEADA (RN-006) ===========================
                Para:       {}
                Desbloqueo: {}
                ===============================================================================
                """, titular.email().value(), desbloqueoEn);
    }

    @Override
    public void enviarAvisoDeSesionRevocadaPorSeguridad(User titular) {
        LOG.info("""

                ================ AVISO DE SESION REVOCADA POR SEGURIDAD =======================
                Para: {}
                Llego un token de refresco ya usado: se revoco la familia completa.
                ===============================================================================
                """, titular.email().value());
    }

    @Override
    public void enviarRestablecimientoDeContrasena(User destinatario, String tokenEnClaro) {
        LOG.info("""

                ================ RESTABLECIMIENTO DE CONTRASENA (consola) =====================
                Para:   {}
                Enlace: {}
                Vence en 30 minutos y sirve una sola vez.
                ===============================================================================
                """, destinatario.email().value(), enlaces.paraRestablecer(tokenEnClaro));
    }

    @Override
    public void enviarAvisoDeContrasenaCambiada(User titular) {
        LOG.info("""

                ================ AVISO DE CONTRASENA CAMBIADA (criterio 20) ===================
                Para: {}
                Se cambio la contrasena y se cerraron todas las sesiones.
                ===============================================================================
                """, titular.email().value());
    }

    @Override
    public void enviarAvisoDeCuentaCerrada(User titular) {
        LOG.info("""

                ================ AVISO DE CUENTA CERRADA (criterio 23) ========================
                Para: {}
                La cuenta se cerro y sus datos quedaron anonimizados.
                ===============================================================================
                """, titular.email().value());
    }
}
