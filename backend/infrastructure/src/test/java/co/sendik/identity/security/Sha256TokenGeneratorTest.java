package co.sendik.identity.security;

import static org.assertj.core.api.Assertions.assertThat;

import co.sendik.identity.port.out.TokenGenerator;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Los tokens de un solo uso: verificacion de correo, restablecimiento y cambio de
 * correo.
 *
 * <p>De este adaptador dependen tres flujos en los que adivinar un token es
 * apoderarse de una cuenta, asi que lo que se prueba es que el valor en claro no
 * es adivinable y que lo que se guarda no es el valor en claro.
 */
class Sha256TokenGeneratorTest {

    private final Sha256TokenGenerator generador = new Sha256TokenGenerator();

    /**
     * Lo que viaja en el enlace y lo que queda en la base de datos no son lo mismo:
     * con la base filtrada, los tokens guardados no sirven para verificar ni para
     * restablecer nada.
     */
    @Test
    void deberia_devolver_el_valor_en_claro_y_su_hash_por_separado() {
        TokenGenerator.GeneratedToken token = generador.generar();

        assertThat(token.hash()).isNotEqualTo(token.valorEnClaro());
        assertThat(token.hash()).isEqualTo(generador.hashearRecibido(token.valorEnClaro()));
    }

    /** SHA-256 en hexadecimal: 64 caracteres, siempre los mismos para la misma entrada. */
    @Test
    void deberia_hashear_de_forma_estable_y_en_hexadecimal() {
        String unaVez = generador.hashearRecibido("token-de-prueba");
        String otraVez = generador.hashearRecibido("token-de-prueba");

        assertThat(unaVez).isEqualTo(otraVez).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void deberia_dar_hashes_distintos_a_tokens_distintos() {
        assertThat(generador.hashearRecibido("uno")).isNotEqualTo(generador.hashearRecibido("dos"));
    }

    /**
     * Base64 apto para URL y sin relleno: el token viaja como parametro de consulta
     * y un "+" o un "=" ahi obliga a codificar y se pierde en la mitad de los
     * clientes de correo.
     */
    @Test
    void deberia_generar_un_valor_que_viaja_entero_en_una_url() {
        assertThat(generador.generar().valorEnClaro()).matches("[A-Za-z0-9_-]+").doesNotContain("=");
    }

    /**
     * 32 bytes de entropia, que en base64 sin relleno son 43 caracteres. No es un
     * detalle cosmetico: es la diferencia entre un token que no se adivina y uno que
     * se recorre. Si alguien recorta el tamano, esta prueba lo dice.
     */
    @Test
    void deberia_llevar_32_bytes_de_entropia() {
        assertThat(generador.generar().valorEnClaro()).hasSize(43);
    }

    @Test
    void nunca_deberia_repetir_un_token() {
        Set<String> vistos = new HashSet<>();

        IntStream.range(0, 500).forEach(i -> vistos.add(generador.generar().valorEnClaro()));

        assertThat(vistos).hasSize(500);
    }
}
