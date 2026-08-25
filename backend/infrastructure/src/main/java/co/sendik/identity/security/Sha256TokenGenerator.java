package co.sendik.identity.security;

import co.sendik.identity.port.out.TokenGenerator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * Genera tokens de un solo uso y su hash.
 *
 * <p>256 bits de {@link SecureRandom}: un token de verificacion es una
 * credencial temporal y adivinarlo equivale a apropiarse de una cuenta.
 *
 * <p>El hash es SHA-256 y no Argon2, a proposito. Argon2 protege contrasenas
 * porque son cortas y adivinables; un token aleatorio de 256 bits no lo es, y
 * usar un algoritmo deliberadamente lento en cada comprobacion de enlace solo
 * regalaria una forma de saturar el servidor. Lo que importa aqui es que quien
 * lea la base de datos no pueda reconstruir el enlace, y para eso SHA-256 basta.
 *
 * <p>Se codifica en base64 sin relleno y apto para URL: el token viaja dentro de
 * un enlace y no debe necesitar escapado.
 */
@Component
public class Sha256TokenGenerator implements TokenGenerator {

    private static final int BYTES_DE_ENTROPIA = 32;

    private final SecureRandom aleatorio = new SecureRandom();

    @Override
    public GeneratedToken generar() {
        byte[] bytes = new byte[BYTES_DE_ENTROPIA];
        aleatorio.nextBytes(bytes);

        String valorEnClaro = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new GeneratedToken(valorEnClaro, hashearRecibido(valorEnClaro));
    }

    @Override
    public String hashearRecibido(String valorEnClaro) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(valorEnClaro.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 es obligatorio en toda plataforma Java. Si falta, algo esta
            // roto muy por debajo de esta aplicacion.
            throw new IllegalStateException("SHA-256 no esta disponible en esta JVM", e);
        }
    }
}
