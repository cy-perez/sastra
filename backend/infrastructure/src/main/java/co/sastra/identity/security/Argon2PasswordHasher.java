package co.sastra.identity.security;

import co.sastra.identity.model.PasswordHash;
import co.sastra.identity.model.RawPassword;
import co.sastra.identity.port.out.PasswordHasher;
import org.jspecify.annotations.Nullable;
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

    /**
     * Hash contra el que se compara cuando no hay cuenta.
     *
     * <p>Se calcula una vez al arrancar en lugar de escribirlo como constante: un
     * hash literal en el codigo invita a copiarlo a otro sitio donde si importe.
     * Ninguna contrasena coincide con el, porque el valor con el que se genero no
     * sale de este constructor.
     */
    private final String senuelo;

    public Argon2PasswordHasher() {
        this.encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        this.senuelo = encoder.encode("senuelo-para-igualar-el-tiempo-de-respuesta");
    }

    @Override
    public PasswordHash hashear(RawPassword contrasena) {
        return new PasswordHash(encoder.encode(contrasena.value()));
    }

    /**
     * Con hash nulo, es decir con un correo que no tiene cuenta, se compara igual
     * contra el senuelo y se devuelve {@code false}.
     *
     * <p>No es una precaucion teorica: Argon2 tarda del orden de cien milisegundos a
     * proposito, asi que responder sin hashear seria visible desde fuera con una
     * sola peticion y convertiria el formulario de acceso en un detector de cuentas
     * (criterio 11 de HU-001).
     */
    @Override
    public boolean coincide(RawPassword contrasena, @Nullable PasswordHash hash) {
        String contra = hash == null ? senuelo : hash.value();
        boolean coincide = encoder.matches(contrasena.value(), contra);

        return hash != null && coincide;
    }
}
