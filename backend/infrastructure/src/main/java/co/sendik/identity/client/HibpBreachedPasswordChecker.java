package co.sendik.identity.client;

import co.sendik.identity.config.PasswordSecurityProperties;
import co.sendik.identity.model.RawPassword;
import co.sendik.identity.port.out.BreachedPasswordChecker;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Comprobacion contra Have I Been Pwned con k-anonimato (ADR-0013).
 *
 * <p>Nunca sale la contrasena, y tampoco su hash completo. Se envian los
 * <strong>cinco primeros caracteres</strong> del SHA-1 y el servicio devuelve
 * todos los sufijos que empiezan asi, unos cientos. La comparacion final ocurre
 * aqui dentro, asi que el servicio no puede saber cual se estaba comprobando.
 *
 * <p>El SHA-1 no es un descuido: es el protocolo que define ese servicio y solo
 * sirve para consultar. El almacenamiento de contrasenas es Argon2id.
 */
@Component
@ConditionalOnProperty(
        prefix = "sendik.password",
        name = "breach-check-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class HibpBreachedPasswordChecker implements BreachedPasswordChecker {

    private static final Logger LOG = LoggerFactory.getLogger(HibpBreachedPasswordChecker.class);
    private static final int LARGO_DEL_PREFIJO = 5;

    private final RestClient cliente;

    public HibpBreachedPasswordChecker(PasswordSecurityProperties propiedades) {
        // Tiempo de espera corto y sin reintentos: si no responde rapido, no
        // responde, y hacer esperar a quien se registra es peor que no comprobar.
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory();
        fabrica.setReadTimeout(propiedades.breachCheckTimeout());

        this.cliente = RestClient.builder()
                .baseUrl(propiedades.breachCheckApiUrl().toString())
                .requestFactory(fabrica)
                .build();
    }

    @Override
    public Resultado verificar(RawPassword contrasena) {
        String hash = sha1(contrasena.value());
        String prefijo = hash.substring(0, LARGO_DEL_PREFIJO);
        String sufijoBuscado = hash.substring(LARGO_DEL_PREFIJO);

        try {
            String respuesta =
                    cliente.get().uri("/{prefijo}", prefijo).retrieve().body(String.class);

            if (respuesta == null) {
                return Resultado.NO_SE_PUDO_COMPROBAR;
            }
            return contiene(respuesta, sufijoBuscado) ? Resultado.FILTRADA : Resultado.LIMPIA;

        } catch (RuntimeException e) {
            // Fallo abierto (ADR-0013). Se registra sin ninguna pista de que
            // contrasena se estaba comprobando.
            LOG.warn("No se pudo consultar la lista de contrasenas filtradas: {}", e.getMessage());
            return Resultado.NO_SE_PUDO_COMPROBAR;
        }
    }

    /** La respuesta son lineas "SUFIJO:VECES". Solo interesa si el sufijo aparece. */
    private static boolean contiene(String respuesta, String sufijoBuscado) {
        for (String linea : respuesta.split("\r?\n")) {
            int separador = linea.indexOf(':');
            String sufijo = separador < 0 ? linea : linea.substring(0, separador);
            if (sufijo.strip().equalsIgnoreCase(sufijoBuscado)) {
                return true;
            }
        }
        return false;
    }

    private static String sha1(String texto) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of()
                    .formatHex(digest.digest(texto.getBytes(StandardCharsets.UTF_8)))
                    .toUpperCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 no esta disponible en esta JVM", e);
        }
    }
}
