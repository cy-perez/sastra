package co.sastra.identity.port.out;

import co.sastra.identity.model.PasswordHash;
import co.sastra.identity.model.RawPassword;
import org.jspecify.annotations.Nullable;

/**
 * Puerto de salida hacia el algoritmo de hash.
 *
 * <p>Es un puerto y no una llamada directa porque el algoritmo caduca: el dia que
 * Argon2id se quede corto, se cambia el adaptador y nada mas
 * (docs/arquitectura/modelo-datos.md).
 */
public interface PasswordHasher {

    PasswordHash hashear(RawPassword contrasena);

    /**
     * Comprueba una contrasena contra su hash.
     *
     * <p><strong>El hash admite nulo y eso es parte del contrato.</strong> Cuando
     * el correo no corresponde a ninguna cuenta no hay hash contra el que
     * comparar, y devolver {@code false} sin gastar tiempo haria que un correo
     * inexistente respondiera antes que uno registrado: el criterio 11 de HU-001
     * pide el mismo tiempo de respuesta en los dos casos. El adaptador compara
     * entonces contra un hash senuelo y devuelve {@code false}.
     *
     * <p>Que la compensacion de tiempo viva en el adaptador y no en el caso de uso
     * es deliberado: solo quien conoce el algoritmo sabe cuanto tarda.
     */
    boolean coincide(RawPassword contrasena, @Nullable PasswordHash hash);
}
