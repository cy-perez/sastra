package co.sendik.shared.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * A quien se le cuenta cada peticion.
 *
 * <p>{@link RateLimiterTest} prueba la cuenta; esto prueba **la clave**, que es la decision
 * que de verdad se tomo: en {@code /api/v1/auth} se cuenta por origen y en
 * {@code /api/v1/users} por sujeto del token. Confundirlas no rompe ninguna cuenta, hace
 * otra cosa peor: contar por IP en las rutas de cuenta deja sin servicio a una oficina
 * entera por lo que haga una sola persona.
 */
class RateLimitInterceptorTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-09-04T15:00:00Z"), ZoneOffset.UTC);
    private static final Duration MINUTO = Duration.ofMinutes(1);

    /** Uno por grupo, con dos peticiones de margen para que agotarlo quepa en una prueba. */
    private final RateLimitInterceptor interceptor = new RateLimitInterceptor(
            new RateLimiter(2, MINUTO, 1000),
            new RateLimiter(2, MINUTO, 1000),
            new RateLimiter(2, MINUTO, 1000),
            new ClientIpHasher(),
            RELOJ);

    @AfterEach
    void limpiarElContexto() {
        SecurityContextHolder.clearContext();
    }

    private static HttpServletRequest peticion(String ruta, String ip) {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", ruta);
        peticion.setRemoteAddr(ip);
        return peticion;
    }

    private static void entrarComo(String sujeto) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(sujeto, "n/a", List.of()));
    }

    private boolean dejaPasar(HttpServletRequest peticion) {
        return interceptor.preHandle(peticion, null, null);
    }

    @Test
    void deberia_ignorar_lo_que_no_es_de_cuenta_ni_de_sesion() {
        HttpServletRequest catalogo = peticion("/api/v1/listings", "10.0.0.1");

        for (int i = 0; i < 10; i++) {
            assertThat(dejaPasar(catalogo)).isTrue();
        }
    }

    @Test
    void deberia_limitar_las_rutas_de_cuenta_por_sujeto_del_token() {
        entrarComo("ana");

        assertThat(dejaPasar(peticion("/api/v1/users/me/listings/summary", "10.0.0.1")))
                .isTrue();
        assertThat(dejaPasar(peticion("/api/v1/users/me/listings/summary", "10.0.0.1")))
                .isTrue();

        assertThatThrownBy(() -> dejaPasar(peticion("/api/v1/users/me/listings/summary", "10.0.0.1")))
                .isInstanceOf(RateLimitExceededException.class);
    }

    /**
     * <strong>La que justifica contar por sujeto.</strong>
     *
     * <p>Ana y Luis salen por la misma IP -una oficina, un operador movil- y Ana agota su
     * cupo. Contando por origen, Luis se quedaria fuera sin haber hecho nada. Contando por
     * sujeto, ni se entera.
     */
    @Test
    void no_deberia_dejar_fuera_a_quien_comparte_salida_con_quien_agoto_su_cupo() {
        entrarComo("ana");
        dejaPasar(peticion("/api/v1/users/me/listings/summary", "10.0.0.1"));
        dejaPasar(peticion("/api/v1/users/me/listings/summary", "10.0.0.1"));
        assertThatThrownBy(() -> dejaPasar(peticion("/api/v1/users/me/listings/summary", "10.0.0.1")))
                .isInstanceOf(RateLimitExceededException.class);

        entrarComo("luis");

        assertThat(dejaPasar(peticion("/api/v1/users/me/listings/summary", "10.0.0.1")))
                .as("la misma IP, otra cuenta: no le toca el limite de nadie")
                .isTrue();
    }

    /** Y cambiar de salida no renueva el cupo, que es la otra mitad de la decision. */
    @Test
    void deberia_seguir_contando_a_la_misma_cuenta_aunque_cambie_de_origen() {
        entrarComo("ana");
        dejaPasar(peticion("/api/v1/users/me/listings/summary", "10.0.0.1"));
        dejaPasar(peticion("/api/v1/users/me/listings/summary", "10.0.0.2"));

        assertThatThrownBy(() -> dejaPasar(peticion("/api/v1/users/me/listings/summary", "10.0.0.3")))
                .isInstanceOf(RateLimitExceededException.class);
    }

    /** Cada ruta lleva su propia cuenta, tambien aqui: agotar una no cierra las demas. */
    @Test
    void deberia_contar_cada_ruta_de_cuenta_por_separado() {
        entrarComo("ana");
        dejaPasar(peticion("/api/v1/users/me/listings/summary", "10.0.0.1"));
        dejaPasar(peticion("/api/v1/users/me/listings/summary", "10.0.0.1"));

        assertThat(dejaPasar(peticion("/api/v1/users/me/listings", "10.0.0.1"))).isTrue();
    }

    /** Sin autenticacion no hay a quien contar: la peticion va a salir 401 de todos modos. */
    @Test
    void deberia_dejar_pasar_una_ruta_de_cuenta_sin_sesion() {
        for (int i = 0; i < 5; i++) {
            assertThat(dejaPasar(peticion("/api/v1/users/me/listings", "10.0.0.1")))
                    .isTrue();
        }
    }

    /** En `auth` no cambia nada: ahi se sigue contando por origen, porque no hay cuenta. */
    @Test
    void deberia_seguir_limitando_las_rutas_de_sesion_por_origen() {
        assertThat(dejaPasar(peticion("/api/v1/auth/session", "10.0.0.1"))).isTrue();
        assertThat(dejaPasar(peticion("/api/v1/auth/session", "10.0.0.1"))).isTrue();

        assertThatThrownBy(() -> dejaPasar(peticion("/api/v1/auth/session", "10.0.0.1")))
                .isInstanceOf(RateLimitExceededException.class);

        assertThat(dejaPasar(peticion("/api/v1/auth/session", "10.0.0.9")))
                .as("otra salida, otra cuenta")
                .isTrue();
    }
}
