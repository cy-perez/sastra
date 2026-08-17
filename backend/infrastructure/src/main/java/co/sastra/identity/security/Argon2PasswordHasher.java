package co.sastra.identity.security;

import co.sastra.identity.model.PasswordHash;
import co.sastra.identity.model.RawPassword;
import co.sastra.identity.port.out.PasswordHasher;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Adaptador de hash con Argon2id, no BCrypt (backend/CLAUDE.md).
 *
 * <p>Se usan los parametros que recomienda Spring Security en lugar de unos
 * propios: elegir a ojo el costo de memoria y las iteraciones de Argon2 es la
 * forma habitual de acabar con un hash mas debil de lo que se cree.
 *
 * <p>El encoder guarda sus parametros dentro del propio hash, asi que subirlos
 * mas adelante no invalida las contrasenas existentes: siguen verificandose con
 * los suyos hasta que la persona la cambie.
 *
 * <p>Requiere BouncyCastle en el classpath. Sin el, la clase existe pero falla al
 * hashear (ADR-0013 documenta la dependencia).
 */
@Component
public class Argon2PasswordHasher implements PasswordHasher {

    private final PasswordEncoder encoder;

    public Argon2PasswordHasher() {
        this.encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Override
    public PasswordHash hashear(RawPassword contrasena) {
        return new PasswordHash(encoder.encode(contrasena.value()));
    }
}
