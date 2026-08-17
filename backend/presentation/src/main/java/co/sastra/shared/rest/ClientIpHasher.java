package co.sastra.shared.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Convierte la direccion IP de quien llama en un hash.
 *
 * <p>La evidencia de consentimiento necesita constar de que la aceptacion vino
 * de algun sitio, no de <em>que</em> sitio. Guardar la IP en claro conservaria un
 * dato de localizacion sin necesidad; el hash sirve igual como prueba y deja de
 * ser un dato de localizacion (docs/operacion/datos-personales.md).
 *
 * <p>Ocurre en el borde a proposito: a partir de aqui la IP en claro no viaja a
 * ninguna capa interior, asi que no puede acabar en un registro por descuido.
 */
@Component
public class ClientIpHasher {

    public @Nullable String hashear(HttpServletRequest peticion) {
        String ip = direccionDe(peticion);
        if (ip == null || ip.isBlank()) {
            return null;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(ip.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no esta disponible en esta JVM", e);
        }
    }

    /**
     * Detras de un balanceador la IP real llega en {@code X-Forwarded-For}. Se
     * toma la primera, que es la del cliente; las siguientes son los saltos
     * intermedios.
     */
    private static @Nullable String direccionDe(HttpServletRequest peticion) {
        String reenviada = peticion.getHeader("X-Forwarded-For");
        if (reenviada != null && !reenviada.isBlank()) {
            return reenviada.split(",", 2)[0].strip();
        }
        return peticion.getRemoteAddr();
    }
}
