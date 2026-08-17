package co.sastra.shared.rest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * La IP se convierte en hash en el borde y no viaja en claro a ninguna capa
 * interior (docs/operacion/datos-personales.md).
 */
class ClientIpHasherTest {

    private final ClientIpHasher hasher = new ClientIpHasher();

    private static MockHttpServletRequest peticionDesde(String ip) {
        MockHttpServletRequest peticion = new MockHttpServletRequest();
        peticion.setRemoteAddr(ip);
        return peticion;
    }

    @Test
    void deberia_devolver_un_hash_y_nunca_la_direccion() {
        String hash = hasher.hashear(peticionDesde("190.85.12.7"));

        assertThat(hash).isNotNull().doesNotContain("190.85.12.7").hasSize(64);
    }

    @Test
    void deberia_dar_el_mismo_hash_para_la_misma_direccion() {
        assertThat(hasher.hashear(peticionDesde("190.85.12.7")))
                .isEqualTo(hasher.hashear(peticionDesde("190.85.12.7")))
                .isNotEqualTo(hasher.hashear(peticionDesde("190.85.12.8")));
    }

    /**
     * Detras de un balanceador la direccion real llega en {@code X-Forwarded-For}.
     * Se toma la primera, que es la del cliente; las siguientes son los saltos
     * intermedios. Sin esto, todas las peticiones compartirian la IP del
     * balanceador y el limite de tasa dejaria fuera a todo el mundo a la vez.
     */
    @Test
    void deberia_preferir_la_primera_direccion_de_x_forwarded_for() {
        MockHttpServletRequest peticion = peticionDesde("10.0.0.1");
        peticion.addHeader("X-Forwarded-For", "190.85.12.7, 10.0.0.9, 10.0.0.1");

        assertThat(hasher.hashear(peticion)).isEqualTo(hasher.hashear(peticionDesde("190.85.12.7")));
    }

    @Test
    void deberia_ignorar_una_cabecera_reenviada_vacia() {
        MockHttpServletRequest peticion = peticionDesde("190.85.12.7");
        peticion.addHeader("X-Forwarded-For", "   ");

        assertThat(hasher.hashear(peticion)).isEqualTo(hasher.hashear(peticionDesde("190.85.12.7")));
    }

    // Sin direccion no hay nada que hashear. No es un error: hay llamadas
    // internas y pruebas que no la traen.
    @Test
    void deberia_devolver_nulo_si_no_hay_direccion() {
        MockHttpServletRequest peticion = new MockHttpServletRequest();
        peticion.setRemoteAddr(null);

        assertThat(hasher.hashear(peticion)).isNull();
    }
}
