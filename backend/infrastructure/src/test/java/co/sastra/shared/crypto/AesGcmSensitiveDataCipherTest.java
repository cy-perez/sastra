package co.sastra.shared.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * El cifrado de datos sensibles (ADR-0020), probado por comportamiento.
 *
 * <p>Las claves de estas pruebas son constantes escritas aqui y no sirven para nada
 * fuera: es codigo propio de criptografia y lo que hay que comprobar es que hace lo
 * que la ADR promete, no que AES funcione.
 */
class AesGcmSensitiveDataCipherTest {

    private static final String CLAVE_V1 = base64De((byte) 1);

    private static final String CLAVE_V2 = base64De((byte) 2);

    private static final String CLAVE_DE_BUSQUEDA = base64De((byte) 9);

    private static final String CEDULA = "1053812947";

    private static String base64De(byte relleno) {
        byte[] clave = new byte[32];
        java.util.Arrays.fill(clave, relleno);
        return Base64.getEncoder().encodeToString(clave);
    }

    private static AesGcmSensitiveDataCipher cifrador() {
        return new AesGcmSensitiveDataCipher(new CryptoProperties(Map.of(1, CLAVE_V1), 1, CLAVE_DE_BUSQUEDA));
    }

    @Test
    void deberia_descifrar_lo_que_cifro() {
        AesGcmSensitiveDataCipher cifrador = cifrador();

        assertThat(cifrador.descifrar(cifrador.cifrar(CEDULA))).isEqualTo(CEDULA);
    }

    /**
     * Es obligatorio, no un detalle: un cifrado que produjera siempre lo mismo
     * revelaria que dos filas comparten valor sin necesidad de descifrar ninguna.
     */
    @Test
    void deberia_producir_un_cifrado_distinto_cada_vez_para_el_mismo_valor() {
        AesGcmSensitiveDataCipher cifrador = cifrador();

        assertThat(cifrador.cifrar(CEDULA).cipher())
                .isNotEqualTo(cifrador.cifrar(CEDULA).cipher());
    }

    /** Y por eso mismo hace falta la huella: esta si es determinista. */
    @Test
    void deberia_producir_la_misma_huella_para_el_mismo_valor() {
        AesGcmSensitiveDataCipher cifrador = cifrador();

        assertThat(cifrador.huella(CEDULA)).isEqualTo(cifrador.huella(CEDULA));
    }

    @Test
    void deberia_producir_huellas_distintas_para_valores_distintos() {
        AesGcmSensitiveDataCipher cifrador = cifrador();

        assertThat(cifrador.huella(CEDULA)).isNotEqualTo(cifrador.huella("1053812948"));
    }

    /** La huella no descifra nada: es de una sola direccion. */
    @Test
    void deberia_producir_una_huella_que_no_contiene_el_valor() {
        byte[] huella = cifrador().huella(CEDULA);

        assertThat(new String(huella, java.nio.charset.StandardCharsets.ISO_8859_1))
                .doesNotContain(CEDULA);
        assertThat(huella).hasSize(32);
    }

    /**
     * La diferencia entre un cifrado autenticado y uno que devuelve basura: una fila
     * alterada a mano no descifra, falla.
     */
    @Test
    void deberia_negarse_a_descifrar_un_texto_alterado() {
        AesGcmSensitiveDataCipher cifrador = cifrador();
        EncryptedValue original = cifrador.cifrar(CEDULA);

        byte[] bytes = Base64.getDecoder().decode(original.cipher());
        bytes[bytes.length - 1] ^= 0x01;
        EncryptedValue alterado = new EncryptedValue(Base64.getEncoder().encodeToString(bytes), original.keyVersion());

        assertThatThrownBy(() -> cifrador.descifrar(alterado))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("alterado");
    }

    @Test
    void deberia_cifrar_con_la_version_vigente() {
        AesGcmSensitiveDataCipher conDosClaves = new AesGcmSensitiveDataCipher(
                new CryptoProperties(Map.of(1, CLAVE_V1, 2, CLAVE_V2), 2, CLAVE_DE_BUSQUEDA));

        assertThat(conDosClaves.cifrar(CEDULA).keyVersion()).isEqualTo(2);
    }

    /**
     * Lo que hace posible rotar sin reescribir la tabla de golpe: lo cifrado con la
     * clave vieja sigue descifrando mientras su clave siga configurada.
     */
    @Test
    void deberia_descifrar_una_fila_cifrada_con_una_version_anterior() {
        EncryptedValue conLaVieja = new AesGcmSensitiveDataCipher(
                        new CryptoProperties(Map.of(1, CLAVE_V1), 1, CLAVE_DE_BUSQUEDA))
                .cifrar(CEDULA);

        AesGcmSensitiveDataCipher despuesDeRotar = new AesGcmSensitiveDataCipher(
                new CryptoProperties(Map.of(1, CLAVE_V1, 2, CLAVE_V2), 2, CLAVE_DE_BUSQUEDA));

        assertThat(despuesDeRotar.descifrar(conLaVieja)).isEqualTo(CEDULA);
    }

    @Test
    void deberia_decirlo_claro_cuando_falta_la_clave_de_una_fila() {
        EncryptedValue conLaVieja = new AesGcmSensitiveDataCipher(
                        new CryptoProperties(Map.of(1, CLAVE_V1), 1, CLAVE_DE_BUSQUEDA))
                .cifrar(CEDULA);

        AesGcmSensitiveDataCipher sinLaVieja =
                new AesGcmSensitiveDataCipher(new CryptoProperties(Map.of(2, CLAVE_V2), 2, CLAVE_DE_BUSQUEDA));

        assertThatThrownBy(() -> sinLaVieja.descifrar(conLaVieja))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("version 1");
    }

    /**
     * El error que no da sintomas: copiar y pegar la misma cadena en dos variables de
     * entorno deja la separacion de claves de ADR-0020 en nada, y todo sigue
     * funcionando igual.
     */
    @Test
    void deberia_negarse_a_arrancar_si_la_clave_de_busqueda_es_una_de_cifrado() {
        CryptoProperties repetida = new CryptoProperties(Map.of(1, CLAVE_V1), 1, CLAVE_V1);

        assertThatThrownBy(() -> new AesGcmSensitiveDataCipher(repetida))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADR-0020");
    }

    @Test
    void deberia_negarse_a_arrancar_si_la_version_vigente_no_tiene_clave() {
        CryptoProperties sinLaVigente = new CryptoProperties(Map.of(1, CLAVE_V1), 7, CLAVE_DE_BUSQUEDA);

        assertThatThrownBy(() -> new AesGcmSensitiveDataCipher(sinLaVigente))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("current-version");
    }

    /**
     * AES admite 128 y 192 bits, asi que una clave corta funcionaria y nadie se
     * enteraria de que la proteccion es menor de la que dice la ADR.
     */
    @Test
    void deberia_exigir_una_clave_de_256_bits() {
        String corta = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() ->
                        new AesGcmSensitiveDataCipher(new CryptoProperties(Map.of(1, corta), 1, CLAVE_DE_BUSQUEDA)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AES-256");
    }

    @Test
    void deberia_rechazar_una_clave_que_no_es_base64() {
        assertThatThrownBy(() -> new AesGcmSensitiveDataCipher(
                        new CryptoProperties(Map.of(1, "no-es-base64-!!"), 1, CLAVE_DE_BUSQUEDA)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deberia_cifrar_un_numero_de_cuenta_igual_que_una_cedula() {
        AesGcmSensitiveDataCipher cifrador = cifrador();

        assertThatCode(() -> cifrador.descifrar(cifrador.cifrar("91500123456"))).doesNotThrowAnyException();
    }
}
