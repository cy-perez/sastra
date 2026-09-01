package co.sendik.identity.client;

import co.sendik.identity.model.Email;
import co.sendik.identity.model.RejectionReason;
import co.sendik.identity.model.RevocationReason;
import co.sendik.identity.model.User;
import co.sendik.identity.port.out.MailSender;
import co.sendik.shared.port.out.MailTransport;
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
 * {@code sendik.mail.provider} vale {@code console}, algo que los perfiles
 * {@code dev} y {@code prod} no hacen.
 */
// El nombre lo comparte con ResendMailSender a proposito: solo uno de los dos
// esta activo, y AsyncMailSender pide "transporteDeCorreo" sin tener que saber
// cual de ellos le toco.
@Component("transporteDeCorreo")
@ConditionalOnProperty(prefix = "sendik.mail", name = "provider", havingValue = "console")
public class ConsoleMailSender implements MailSender, MailTransport {

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

    @Override
    public void enviarConfirmacionDeCorreoNuevo(User titular, Email destino, String tokenEnClaro) {
        LOG.info("""

                ================ CONFIRMACION DE CORREO NUEVO (criterio 21) ===================
                Para:   {}
                Enlace: {}
                Hasta que se abra, la cuenta conserva su correo anterior.
                ===============================================================================
                """, destino.value(), enlaces.paraCambioDeCorreo(tokenEnClaro));
    }

    @Override
    public void enviarAvisoDeIntentoDeCambioAEsteCorreo(User titular) {
        LOG.info("""

                ================ INTENTO DE CAMBIO A ESTE CORREO (criterio 21) ================
                Para: {}
                Alguien intento mudar su cuenta a este correo, que ya tiene una.
                ===============================================================================
                """, titular.email().value());
    }

    @Override
    public void enviarAvisoDeVerificacionRecibida(User titular) {
        registrar("SOLICITUD DE VERIFICACION RECIBIDA (criterio 6)", titular, "");
    }

    @Override
    public void enviarAvisoDeVerificacionAprobada(User titular) {
        registrar("VERIFICACION APROBADA (criterio 8)", titular, "Ya es vendedor verificado.");
    }

    @Override
    public void enviarAvisoDeVerificacionRechazada(
            User titular, RejectionReason motivo, String nota, int intentosRestantes) {
        registrar(
                "VERIFICACION RECHAZADA (criterio 7)",
                titular,
                "Motivo: " + motivo + ". Intentos restantes: " + intentosRestantes);
    }

    @Override
    public void enviarAvisoDeVerificacionRevocada(User titular, RevocationReason motivo, String nota) {
        registrar("VERIFICACION REVOCADA (RN-013)", titular, "Motivo: " + motivo);
    }

    /**
     * El envio generico, que aqui es imprimirlo. Ver {@link MailTransport} y ADR-0023.
     *
     * <p>Imprime el asunto y no el cuerpo: el cuerpo es HTML y llena la consola. Quien
     * prueba un correo en desarrollo necesita saber que salio y para quien.
     */
    @Override
    public void enviar(String destinatario, String asunto, String html) {
        LOG.info("""

                ================ CORREO ({}) =================================================
                Para:   {}
                Asunto: {}
                ===============================================================================
                """, "adaptador de consola", destinatario, asunto);
    }

    /**
     * Un formato para los cuatro avisos de verificacion. La nota del moderador **no se
     * imprime**: es texto de una persona sobre otra persona y el registro no es sitio
     * para eso (docs/operacion/datos-personales.md).
     */
    private static void registrar(String titulo, User titular, String detalle) {
        LOG.info("""

                ================ {} =======================
                Para: {}
                {}
                ===============================================================================
                """, titulo, titular.email().value(), detalle);
    }

    @Override
    public void enviarAvisoDeCorreoCambiado(User titular, Email anterior) {
        LOG.info("""

                ================ AVISO DE CORREO CAMBIADO (criterio 21) =======================
                Para: {}
                La cuenta ahora usa otro correo.
                ===============================================================================
                """, anterior.value());
    }
}
