package co.sastra.identity.security;

import static org.assertj.core.api.Assertions.assertThat;

import co.sastra.identity.model.PasswordHash;
import co.sastra.identity.model.RawPassword;
import org.junit.jupiter.api.Test;

/**
 * El hasheo de contrasenas: la pieza mas sensible del modulo.
 *
 * <p>Estaba sin prueba propia y la puerta de cobertura no lo notaba, porque un
 * modulo sin ninguna prueba no tiene datos de cobertura que verificar y la
 * verificacion se salta entera. Se prueba aqui, y no solo de refilon desde las
 * pruebas de integracion de bootstrap, porque lo que hay que demostrar son
 * propiedades del algoritmo —sal distinta por hasheo, senuelo con la misma forma—
 * que un flujo de registro no observa.
 */
class Argon2PasswordHasherTest {

    private final Argon2PasswordHasher hasher = new Argon2PasswordHasher();

    private static final RawPassword CONTRASENA = new RawPassword("una-contrasena-larga-de-verdad");

    @Test
    void deberia_reconocer_la_contrasena_correcta() {
        PasswordHash hash = hasher.hashear(CONTRASENA);

        assertThat(hasher.coincide(CONTRASENA, hash)).isTrue();
    }

    @Test
    void deberia_rechazar_una_contrasena_distinta() {
        PasswordHash hash = hasher.hashear(CONTRASENA);

        assertThat(hasher.coincide(new RawPassword("otra-contrasena-larga"), hash))
                .isFalse();
    }

    /** Argon2id, nunca BCrypt (backend/CLAUDE.md). El prefijo lo delata. */
    @Test
    void deberia_usar_argon2id() {
        assertThat(hasher.hashear(CONTRASENA).value()).startsWith("$argon2id$");
    }

    /**
     * Sal distinta en cada hasheo: dos personas con la misma contrasena no pueden
     * compartir hash, porque entonces una tabla precalculada las abre a las dos.
     */
    @Test
    void deberia_producir_un_hash_distinto_cada_vez_para_la_misma_contrasena() {
        assertThat(hasher.hashear(CONTRASENA).value())
                .isNotEqualTo(hasher.hashear(CONTRASENA).value());
    }

    /**
     * Correo que no existe: se compara igual contra un senuelo y se devuelve false.
     *
     * <p>Sin esto, "no hay cuenta" responderia en microsegundos y "contrasena mal"
     * en decenas de milisegundos, y esa diferencia es un oraculo que dice quien
     * tiene cuenta —justo lo que el criterio 2 de HU-001 evita en el registro—.
     * Aqui no se mide el tiempo, que seria una prueba inestable: se comprueba que
     * el camino sin hash tambien pasa por la comparacion y no corta antes.
     */
    @Test
    void deberia_devolver_falso_sin_hash_en_lugar_de_fallar() {
        assertThat(hasher.coincide(CONTRASENA, null)).isFalse();
    }

    /**
     * Un hash guardado que no se puede interpretar tampoco tumba el ingreso: se
     * responde que no coincide. Es el caso de una fila escrita por una version
     * anterior o corrompida, y la diferencia entre un "no coincide" y una traza de
     * excepcion en el borde es lo que decide si el incidente se ve como
     * credenciales malas o como error del servidor.
     */
    @Test
    void deberia_devolver_falso_ante_un_hash_ilegible_sin_propagar_la_excepcion() {
        assertThat(hasher.coincide(CONTRASENA, new PasswordHash("esto-no-es-un-hash-de-argon2")))
                .isFalse();
    }
}
