package co.sendik.identity.client;

import co.sendik.identity.model.Email;
import co.sendik.identity.model.RejectionReason;
import co.sendik.identity.model.User;
import co.sendik.identity.port.out.MailSender;
import co.sendik.shared.port.out.MailTransport;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Saca el envio de correo del hilo de la peticion.
 *
 * <p><strong>Es una correccion de seguridad, no una optimizacion.</strong> El
 * criterio 11 exige que un correo que no existe y una contrasena equivocada
 * tarden lo mismo. Con el envio en el hilo de la peticion, el quinto intento
 * fallido contra una cuenta que si existe manda el aviso de bloqueo y espera al
 * proveedor: cientos de milisegundos mas que contra un correo sin cuenta. Cinco
 * peticiones bastaban para saber quien tiene cuenta en Sendik.
 *
 * <p>De paso arregla lo otro: una caida del proveedor anadia hasta diez segundos
 * de espera a un ingreso, que es el tiempo de espera del cliente HTTP.
 *
 * <p>Envuelve al adaptador de verdad en lugar de que cada uno se vuelva asincrono
 * por su cuenta: asi la decision esta en un solo sitio y los adaptadores siguen
 * siendo lo que son, un POST y un registro por consola.
 */
@Primary
@Component
public class AsyncMailSender implements MailSender, MailTransport {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncMailSender.class);

    /** Dos hilos bastan: son correos sueltos, no un boletin. */
    private static final int HILOS = 2;

    private static final int COLA = 500;

    private static final Duration ESPERA_AL_CERRAR = Duration.ofSeconds(10);

    private final MailSender transporte;

    /**
     * El mismo bean, visto por el otro puerto.
     *
     * <p>Se pide dos veces a proposito: {@code ConsoleMailSender} y {@code ResendMailSender}
     * implementan los dos, y pedirlo asi deja escrito que esta clase difiere las dos cosas.
     * La alternativa era un {@code instanceof} y una conversion, que es lo mismo escondido.
     */
    private final MailTransport generico;

    private final ThreadPoolExecutor ejecutor;

    public AsyncMailSender(
            @Qualifier("transporteDeCorreo") MailSender transporte,
            @Qualifier("transporteDeCorreo") MailTransport generico) {
        this.transporte = transporte;
        this.generico = generico;
        this.ejecutor = new ThreadPoolExecutor(
                HILOS,
                HILOS,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(COLA),
                unHilo(),
                // Si la cola se llena, lo envia el hilo que lo pidio. Es peor para
                // la latencia y devuelve la diferencia de tiempos del criterio 11,
                // pero perder un correo de verificacion deja a alguien sin poder
                // activar su cuenta. Una cola de 500 llena ya es un incidente.
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    private static java.util.concurrent.ThreadFactory unHilo() {
        return tarea -> {
            Thread hilo = new Thread(tarea, "correo-transaccional");
            // De demonio: no debe impedir que la aplicacion termine.
            hilo.setDaemon(true);
            return hilo;
        };
    }

    @Override
    public void enviarAvisoDeVerificacionRecibida(User titular) {
        enDiferido("aviso de verificacion recibida", () -> transporte.enviarAvisoDeVerificacionRecibida(titular));
    }

    @Override
    public void enviarAvisoDeVerificacionAprobada(User titular) {
        enDiferido("aviso de verificacion aprobada", () -> transporte.enviarAvisoDeVerificacionAprobada(titular));
    }

    @Override
    public void enviarAvisoDeVerificacionRechazada(
            User titular, RejectionReason motivo, String nota, int intentosRestantes) {
        enDiferido(
                "aviso de verificacion rechazada",
                () -> transporte.enviarAvisoDeVerificacionRechazada(titular, motivo, nota, intentosRestantes));
    }

    @Override
    public void enviarAvisoDeVerificacionRevocada(User titular, RejectionReason motivo, String nota) {
        enDiferido(
                "aviso de verificacion revocada",
                () -> transporte.enviarAvisoDeVerificacionRevocada(titular, motivo, nota));
    }

    @Override
    public void enviarVerificacionDeCorreo(User destinatario, String tokenEnClaro) {
        enDiferido("verificacion de correo", () -> transporte.enviarVerificacionDeCorreo(destinatario, tokenEnClaro));
    }

    @Override
    public void enviarAvisoDeRegistroConCorreoExistente(User titular) {
        enDiferido("aviso de registro duplicado", () -> transporte.enviarAvisoDeRegistroConCorreoExistente(titular));
    }

    @Override
    public void enviarAvisoDeCuentaBloqueada(User titular, Instant desbloqueoEn) {
        enDiferido("aviso de cuenta bloqueada", () -> transporte.enviarAvisoDeCuentaBloqueada(titular, desbloqueoEn));
    }

    @Override
    public void enviarAvisoDeSesionRevocadaPorSeguridad(User titular) {
        enDiferido("aviso de sesion revocada", () -> transporte.enviarAvisoDeSesionRevocadaPorSeguridad(titular));
    }

    @Override
    public void enviarRestablecimientoDeContrasena(User destinatario, String tokenEnClaro) {
        enDiferido(
                "restablecimiento de contrasena",
                () -> transporte.enviarRestablecimientoDeContrasena(destinatario, tokenEnClaro));
    }

    /**
     * Criterio 20. En diferido como los demas, y aqui importa el doble: es el
     * ultimo paso del restablecimiento, dentro de su transaccion, y esperar al
     * proveedor alargaria una operacion que ya escribio en tres tablas.
     */
    @Override
    public void enviarAvisoDeContrasenaCambiada(User titular) {
        enDiferido("aviso de contrasena cambiada", () -> transporte.enviarAvisoDeContrasenaCambiada(titular));
    }

    @Override
    public void enviarAvisoDeCuentaCerrada(User titular) {
        enDiferido("aviso de cuenta cerrada", () -> transporte.enviarAvisoDeCuentaCerrada(titular));
    }

    @Override
    public void enviarConfirmacionDeCorreoNuevo(User titular, Email destino, String tokenEnClaro) {
        enDiferido(
                "confirmacion de correo nuevo",
                () -> transporte.enviarConfirmacionDeCorreoNuevo(titular, destino, tokenEnClaro));
    }

    @Override
    public void enviarAvisoDeIntentoDeCambioAEsteCorreo(User titular) {
        enDiferido("aviso de intento de cambio", () -> transporte.enviarAvisoDeIntentoDeCambioAEsteCorreo(titular));
    }

    @Override
    public void enviarAvisoDeCorreoCambiado(User titular, Email anterior) {
        enDiferido("aviso de correo cambiado", () -> transporte.enviarAvisoDeCorreoCambiado(titular, anterior));
    }

    /**
     * Tambien el correo generico sale del hilo de la peticion. ADR-0023.
     *
     * <p>Es lo que hace que un aviso de moderacion no le sume la latencia del proveedor a
     * la peticion del moderador, igual que ya pasaba con los de identidad.
     */
    @Override
    public void enviar(String destinatario, String asunto, String cuerpoHtml) {
        enDiferido("aviso de otro contexto", () -> generico.enviar(destinatario, asunto, cuerpoHtml));
    }

    /**
     * Nada de lo que pase aqui puede volver al hilo de la peticion.
     *
     * <p>El puerto ya promete no lanzar, y ademas para cuando esto corra la
     * respuesta ya se envio: una excepcion que escapara solo llegaria al manejador
     * de excepciones del hilo, sin nadie a quien contarselo. Se registra el tipo de
     * correo, nunca el destinatario ni el enlace.
     */
    private void enDiferido(String queCorreo, Runnable envio) {
        ejecutor.execute(() -> {
            try {
                envio.run();
            } catch (RuntimeException e) {
                LOG.error(
                        "Fallo el envio en diferido de un correo de {}: {}",
                        queCorreo,
                        e.getClass().getName());
            }
        });
    }

    /**
     * Al apagar se espera a lo que quede en la cola.
     *
     * <p>Sin esto, un despliegue en mitad de un registro deja a alguien sin su
     * correo de verificacion y sin forma de saber que nunca salio.
     */
    @PreDestroy
    void vaciarLaCola() {
        ejecutor.shutdown();
        try {
            if (!ejecutor.awaitTermination(ESPERA_AL_CERRAR.toSeconds(), TimeUnit.SECONDS)) {
                LOG.warn(
                        "Quedaron {} correos sin enviar al apagar",
                        ejecutor.getQueue().size());
                ejecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ejecutor.shutdownNow();
        }
    }
}
