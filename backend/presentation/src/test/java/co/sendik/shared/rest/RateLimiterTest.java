package co.sendik.shared.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private static final Instant AHORA = Instant.parse("2026-08-17T15:00:00Z");
    private static final Duration MINUTO = Duration.ofMinutes(1);

    private static RateLimiter deTres() {
        return new RateLimiter(3, MINUTO, 1000);
    }

    @Test
    void deberia_dejar_pasar_hasta_el_maximo() {
        RateLimiter limite = deTres();

        for (int i = 0; i < 3; i++) {
            assertThat(limite.registrar("ana", AHORA)).isEmpty();
        }
    }

    @Test
    void deberia_rechazar_la_que_pasa_del_maximo() {
        RateLimiter limite = deTres();
        for (int i = 0; i < 3; i++) {
            limite.registrar("ana", AHORA);
        }

        assertThat(limite.registrar("ana", AHORA)).contains(MINUTO);
    }

    // Cada clave lleva su propia cuenta: el limite de uno no puede dejar fuera a
    // otro que no ha hecho nada.
    @Test
    void no_deberia_mezclar_las_cuentas_de_claves_distintas() {
        RateLimiter limite = deTres();
        for (int i = 0; i < 4; i++) {
            limite.registrar("ana", AHORA);
        }

        assertThat(limite.registrar("beto", AHORA)).isEmpty();
    }

    @Test
    void deberia_empezar_de_cero_al_abrirse_la_ventana_siguiente() {
        RateLimiter limite = deTres();
        for (int i = 0; i < 4; i++) {
            limite.registrar("ana", AHORA);
        }

        assertThat(limite.registrar("ana", AHORA.plus(MINUTO))).isEmpty();
    }

    /**
     * La espera dice cuanto falta para la ventana siguiente, no la ventana entera:
     * es lo que acaba en {@code Retry-After}, y sobrar de mas manda a la persona a
     * esperar mas de lo que hace falta.
     */
    @Test
    void deberia_informar_lo_que_queda_de_ventana_y_no_la_ventana_entera() {
        RateLimiter limite = deTres();
        for (int i = 0; i < 3; i++) {
            limite.registrar("ana", AHORA);
        }

        Optional<Duration> espera = limite.registrar("ana", AHORA.plusSeconds(50));

        assertThat(espera).contains(Duration.ofSeconds(10));
    }

    /**
     * La espera siempre es positiva: si la ventana hubiera vencido, se habria
     * reiniciado y no habria rechazo. Puede quedar en milisegundos, y esta bien:
     * redondear al segundo es cosa de quien escribe {@code Retry-After}, que solo
     * admite segundos enteros, y eso lo hace ApiExceptionHandler.
     */
    @Test
    void deberia_devolver_una_espera_siempre_positiva() {
        RateLimiter limite = new RateLimiter(1, Duration.ofSeconds(1), 1000);
        limite.registrar("ana", AHORA);

        assertThat(limite.registrar("ana", AHORA.plusMillis(999))).contains(Duration.ofMillis(1));
    }

    /**
     * El techo de claves es lo que impide que la defensa sea la via de ataque: sin
     * el, quien varie su origen a voluntad hace crecer el mapa sin final.
     */
    @Test
    void deberia_soltar_las_claves_vencidas_al_pasar_del_techo() {
        RateLimiter limite = new RateLimiter(5, MINUTO, 2);

        for (int i = 0; i < 10; i++) {
            limite.registrar("origen-" + i, AHORA);
        }
        // Una peticion posterior a la ventana dispara el barrido de lo vencido.
        limite.registrar("otro", AHORA.plus(MINUTO).plusSeconds(1));

        // Lo vencido se fue: la cuenta de un origen viejo vuelve a empezar.
        assertThat(limite.registrar("origen-0", AHORA.plus(MINUTO).plusSeconds(1)))
                .isEmpty();
    }

    @Test
    void deberia_rechazar_una_configuracion_sin_sentido() {
        assertThatThrownBy(() -> new RateLimiter(0, MINUTO, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimiter(3, Duration.ZERO, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimiter(3, MINUTO.negated(), 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimiter(3, MINUTO, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
