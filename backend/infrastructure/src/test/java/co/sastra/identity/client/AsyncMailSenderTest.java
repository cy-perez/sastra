package co.sastra.identity.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import co.sastra.identity.model.BirthDate;
import co.sastra.identity.model.DisplayName;
import co.sastra.identity.model.Email;
import co.sastra.identity.model.RejectionReason;
import co.sastra.identity.model.User;
import co.sastra.identity.model.UserId;
import co.sastra.identity.model.UserLocale;
import co.sastra.identity.port.out.MailSender;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * El envio en diferido de los correos transaccionales.
 *
 * <p>Lo que se protege aqui es el criterio 11 y, sobre todo, que un proveedor de
 * correo lento o caido no tumbe el registro. Si el envio fuera sincrono, el tiempo
 * de respuesta del registro delataria si el correo existe —porque el camino con
 * cuenta manda un correo y el otro tambien, pero no los mismos— y una caida de
 * Resend devolveria error a quien acaba de crear su cuenta, que si quedo creada.
 *
 * <p>Las pruebas esperan con un cerrojo y no con una pausa: una pausa fija seria
 * lenta cuando sobra y falsa cuando la maquina va cargada.
 */
class AsyncMailSenderTest {

    private static final Instant AHORA = Instant.parse("2026-08-20T15:00:00Z");

    /** Transporte de mentira que anota lo que le llega y puede fallar a voluntad. */
    private static final class TransporteEspia implements MailSender {

        private final List<String> enviados = new CopyOnWriteArrayList<>();
        private final CountDownLatch llegadas;
        private final boolean falla;

        private TransporteEspia(int cuantosSeEsperan, boolean falla) {
            this.llegadas = new CountDownLatch(cuantosSeEsperan);
            this.falla = falla;
        }

        private void anotar(String que) {
            enviados.add(que);
            llegadas.countDown();
            if (falla) {
                throw new IllegalStateException("el proveedor de correo esta caido");
            }
        }

        private boolean esperar() throws InterruptedException {
            return llegadas.await(5, TimeUnit.SECONDS);
        }

        @Override
        public void enviarVerificacionDeCorreo(User destinatario, String tokenEnClaro) {
            anotar("verificacion:" + tokenEnClaro);
        }

        @Override
        public void enviarAvisoDeRegistroConCorreoExistente(User titular) {
            anotar("registro-duplicado");
        }

        @Override
        public void enviarAvisoDeCuentaBloqueada(User titular, Instant desbloqueoEn) {
            anotar("bloqueada");
        }

        @Override
        public void enviarAvisoDeSesionRevocadaPorSeguridad(User titular) {
            anotar("sesion-revocada");
        }

        @Override
        public void enviarRestablecimientoDeContrasena(User destinatario, String tokenEnClaro) {
            anotar("restablecimiento:" + tokenEnClaro);
        }

        @Override
        public void enviarAvisoDeContrasenaCambiada(User titular) {
            anotar("contrasena-cambiada");
        }

        @Override
        public void enviarAvisoDeCuentaCerrada(User titular) {
            anotar("cuenta-cerrada");
        }

        @Override
        public void enviarConfirmacionDeCorreoNuevo(User titular, Email destino, String tokenEnClaro) {
            anotar("correo-nuevo:" + destino.value());
        }

        @Override
        public void enviarAvisoDeIntentoDeCambioAEsteCorreo(User titular) {
            anotar("intento-de-cambio");
        }

        @Override
        public void enviarAvisoDeVerificacionRecibida(User titular) {
            anotar("verificacion-recibida");
        }

        @Override
        public void enviarAvisoDeVerificacionAprobada(User titular) {
            anotar("verificacion-aprobada");
        }

        @Override
        public void enviarAvisoDeVerificacionRechazada(
                User titular, RejectionReason motivo, String nota, int intentosRestantes) {
            anotar("verificacion-rechazada");
        }

        @Override
        public void enviarAvisoDeVerificacionRevocada(User titular, RejectionReason motivo, String nota) {
            anotar("verificacion-revocada");
        }

        @Override
        public void enviarAvisoDeCorreoCambiado(User titular, Email anterior) {
            anotar("correo-cambiado:" + anterior.value());
        }
    }

    private static User cuenta() {
        return User.registrar(
                UserId.nuevo(),
                new Email("ana@correo.co"),
                new DisplayName("Ana Maria"),
                new BirthDate(LocalDate.of(1990, 3, 4)),
                UserLocale.ES,
                LocalDate.of(2026, 8, 20),
                AHORA);
    }

    @Test
    void deberia_entregar_el_correo_al_transporte_con_su_token() throws InterruptedException {
        TransporteEspia espia = new TransporteEspia(1, false);
        AsyncMailSender diferido = new AsyncMailSender(espia);

        diferido.enviarVerificacionDeCorreo(cuenta(), "token-en-claro");

        assertThat(espia.esperar()).isTrue();
        assertThat(espia.enviados).containsExactly("verificacion:token-en-claro");
    }

    /**
     * Los diez mensajes del sistema pasan por el diferido. Se prueban todos porque
     * lo facil de olvidar al anadir un correo nuevo es justamente delegarlo, y un
     * mensaje que se queda sin enviar no rompe nada visible.
     */
    @Test
    void deberia_delegar_todos_los_mensajes_del_sistema() throws InterruptedException {
        TransporteEspia espia = new TransporteEspia(10, false);
        AsyncMailSender diferido = new AsyncMailSender(espia);
        User titular = cuenta();

        diferido.enviarVerificacionDeCorreo(titular, "t1");
        diferido.enviarAvisoDeRegistroConCorreoExistente(titular);
        diferido.enviarAvisoDeCuentaBloqueada(titular, AHORA);
        diferido.enviarAvisoDeSesionRevocadaPorSeguridad(titular);
        diferido.enviarRestablecimientoDeContrasena(titular, "t2");
        diferido.enviarAvisoDeContrasenaCambiada(titular);
        diferido.enviarAvisoDeCuentaCerrada(titular);
        diferido.enviarConfirmacionDeCorreoNuevo(titular, new Email("nuevo@correo.co"), "t3");
        diferido.enviarAvisoDeIntentoDeCambioAEsteCorreo(titular);
        diferido.enviarAvisoDeCorreoCambiado(titular, new Email("viejo@correo.co"));

        assertThat(espia.esperar()).isTrue();
        assertThat(espia.enviados)
                .containsExactlyInAnyOrder(
                        "verificacion:t1",
                        "registro-duplicado",
                        "bloqueada",
                        "sesion-revocada",
                        "restablecimiento:t2",
                        "contrasena-cambiada",
                        "cuenta-cerrada",
                        "correo-nuevo:nuevo@correo.co",
                        "intento-de-cambio",
                        "correo-cambiado:viejo@correo.co");
    }

    /**
     * Un proveedor caido no llega a quien se registra. La cuenta ya quedo creada:
     * devolver error ahora seria mentir sobre lo que paso y ademas invitaria a
     * reintentar el registro con un correo que ya existe.
     */
    @Test
    void no_deberia_propagar_el_fallo_del_proveedor_a_quien_llamo() throws InterruptedException {
        TransporteEspia caido = new TransporteEspia(1, true);
        AsyncMailSender diferido = new AsyncMailSender(caido);

        assertThatCode(() -> diferido.enviarVerificacionDeCorreo(cuenta(), "token"))
                .doesNotThrowAnyException();

        // Y se intento de verdad: no propagar no puede significar no enviar.
        assertThat(caido.esperar()).isTrue();
    }

    /**
     * Al apagar se vacia la cola. Sin esto, un despliegue en el segundo equivocado
     * se lleva por delante el correo de verificacion de quien acababa de
     * registrarse, y esa persona se queda sin poder activar su cuenta.
     */
    @Test
    void deberia_vaciar_la_cola_antes_de_apagarse() throws InterruptedException {
        TransporteEspia espia = new TransporteEspia(20, false);
        AsyncMailSender diferido = new AsyncMailSender(espia);
        User titular = cuenta();

        for (int i = 0; i < 20; i++) {
            diferido.enviarAvisoDeContrasenaCambiada(titular);
        }
        diferido.vaciarLaCola();

        assertThat(espia.enviados).hasSize(20);
    }
}
