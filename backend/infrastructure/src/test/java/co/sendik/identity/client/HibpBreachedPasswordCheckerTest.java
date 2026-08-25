package co.sendik.identity.client;

import static org.assertj.core.api.Assertions.assertThat;

import co.sendik.identity.config.PasswordSecurityProperties;
import co.sendik.identity.model.RawPassword;
import co.sendik.identity.port.out.BreachedPasswordChecker;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Contrasenas filtradas por k-anonimato (ADR-0013).
 *
 * <p>Se prueba contra un servidor local de verdad y no con un simulacro de cliente,
 * porque las dos cosas que hay que demostrar solo se ven en el cable: que lo que
 * sale de la maquina es el prefijo de cinco caracteres y nada mas, y que cuando el
 * servicio no responde el registro sigue adelante.
 *
 * <p>La primera es la razon de ser de ADR-0013. Mandar el hash completo funcionaria
 * igual de bien y convertiria cada registro en una entrega de la contrasena de la
 * persona a un tercero; ninguna prueba sobre el valor devuelto lo notaria.
 */
class HibpBreachedPasswordCheckerTest {

    private static final RawPassword CONTRASENA = new RawPassword("una-contrasena-larga-de-verdad");

    private HttpServer servidor;
    private final List<String> rutasPedidas = new CopyOnWriteArrayList<>();

    /** Lo que el servicio real devolveria: sufijos del hash y su numero de apariciones. */
    private String cuerpo = "";

    private int codigo = 200;

    @BeforeEach
    void levantarServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/range", intercambio -> {
            rutasPedidas.add(intercambio.getRequestURI().getPath());
            byte[] respuesta = cuerpo.getBytes(StandardCharsets.UTF_8);
            intercambio.sendResponseHeaders(codigo, respuesta.length);
            try (OutputStream salida = intercambio.getResponseBody()) {
                salida.write(respuesta);
            }
        });
        servidor.start();
    }

    @AfterEach
    void apagarServidor() {
        servidor.stop(0);
    }

    private HibpBreachedPasswordChecker comprobador(Duration espera) {
        URI base = URI.create("http://127.0.0.1:" + servidor.getAddress().getPort() + "/range");
        return new HibpBreachedPasswordChecker(new PasswordSecurityProperties(true, espera, base));
    }

    private HibpBreachedPasswordChecker comprobador() {
        return comprobador(Duration.ofSeconds(2));
    }

    private static String sha1De(String texto) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of()
                    .formatHex(digest.digest(texto.getBytes(StandardCharsets.UTF_8)))
                    .toUpperCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * La prueba que justifica la ADR: por el cable va el prefijo de cinco
     * caracteres, nunca el hash completo ni la contrasena.
     */
    @Test
    void solo_deberia_enviar_los_cinco_primeros_caracteres_del_hash() {
        String hash = sha1De(CONTRASENA.value());

        comprobador().verificar(CONTRASENA);

        assertThat(rutasPedidas).containsExactly("/range/" + hash.substring(0, 5));
        assertThat(rutasPedidas.getFirst()).doesNotContain(hash).doesNotContain(CONTRASENA.value());
    }

    @Test
    void deberia_reconocer_una_contrasena_que_esta_en_la_lista() {
        String sufijo = sha1De(CONTRASENA.value()).substring(5);
        cuerpo = "0000000000000000000000000000000000A:3\r\n" + sufijo + ":42\r\n";

        assertThat(comprobador().verificar(CONTRASENA)).isEqualTo(BreachedPasswordChecker.Resultado.FILTRADA);
    }

    @Test
    void deberia_dar_por_limpia_una_contrasena_que_no_aparece() {
        cuerpo = "0000000000000000000000000000000000A:3\r\n0000000000000000000000000000000000B:9\r\n";

        assertThat(comprobador().verificar(CONTRASENA)).isEqualTo(BreachedPasswordChecker.Resultado.LIMPIA);
    }

    /**
     * El servicio real responde en mayusculas, pero eso es una cortesia suya y no
     * un contrato: comparar sensible a mayusculas dejaria pasar como limpia una
     * contrasena filtrada el dia que cambiara el formato.
     */
    @Test
    void deberia_comparar_sin_distinguir_mayusculas() {
        cuerpo = sha1De(CONTRASENA.value()).substring(5).toLowerCase(Locale.ROOT) + ":42";

        assertThat(comprobador().verificar(CONTRASENA)).isEqualTo(BreachedPasswordChecker.Resultado.FILTRADA);
    }

    /**
     * Falla abierto (ADR-0013): si el servicio se cae, quien se registra entra. La
     * alternativa es que un tercero caido cierre el registro de Sendik.
     */
    @Test
    void deberia_fallar_abierto_cuando_el_servicio_responde_con_error() {
        codigo = 503;

        assertThat(comprobador().verificar(CONTRASENA))
                .isEqualTo(BreachedPasswordChecker.Resultado.NO_SE_PUDO_COMPROBAR);
    }

    /** Lo mismo cuando responde vacio: no se sabe, y no saber no es estar limpia. */
    @Test
    void deberia_distinguir_no_se_pudo_comprobar_de_limpia() {
        codigo = 500;

        assertThat(comprobador().verificar(CONTRASENA))
                .isNotEqualTo(BreachedPasswordChecker.Resultado.LIMPIA)
                .isNotEqualTo(BreachedPasswordChecker.Resultado.FILTRADA);
    }
}
