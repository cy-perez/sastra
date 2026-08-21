package co.sastra.identity.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.sastra.shared.file.FileKey;
import org.junit.jupiter.api.Test;

/** Los objetos de valor de la verificacion: lo que aceptan y lo que no dejan escribir. */
class VerificationValueObjectsTest {

    // --- Numero de documento -------------------------------------------------

    @Test
    void deberia_normalizar_el_numero_del_documento_quitando_puntos_y_espacios() {
        // Si no se normalizara, la misma persona podria quedar verificada dos veces
        // escribiendo su cedula distinto, y eso rompe RN-010 sin que nadie lo note.
        assertThat(new IdentityDocumentNumber("1.234.567 ").value()).isEqualTo("1234567");
        assertThat(new IdentityDocumentNumber("1234567")).isEqualTo(new IdentityDocumentNumber("1.234.567"));
    }

    @Test
    void deberia_rechazar_un_numero_de_documento_con_letras() {
        assertThatThrownBy(() -> new IdentityDocumentNumber("12A4567"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("digitos");
    }

    @Test
    void deberia_rechazar_un_numero_de_documento_demasiado_corto_o_largo() {
        assertThatThrownBy(() -> new IdentityDocumentNumber("123")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdentityDocumentNumber("1234567890123456"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Lo unico que sale hacia la pantalla (criterio 11 de HU-002, RN-046).
     */
    @Test
    void deberia_entregar_solo_los_cuatro_ultimos_digitos_del_documento() {
        assertThat(new IdentityDocumentNumber("1234567").ultimosCuatro()).isEqualTo("4567");
    }

    /**
     * Basta interpolar el objeto en un mensaje para que el numero acabe en Cloud
     * Logging, y `datos-personales.md` lo prohibe tambien parcialmente y tambien en
     * depuracion.
     */
    @Test
    void deberia_ocultar_el_numero_del_documento_al_convertirlo_en_texto() {
        String texto = new IdentityDocumentNumber("1234567").toString();

        assertThat(texto).doesNotContain("1234567").contains("4567");
    }

    // --- Numero de cuenta ----------------------------------------------------

    @Test
    void deberia_normalizar_el_numero_de_la_cuenta() {
        assertThat(new BankAccountNumber("123-456-7890").value()).isEqualTo("1234567890");
    }

    @Test
    void deberia_ocultar_el_numero_de_la_cuenta_al_convertirlo_en_texto() {
        String texto = new BankAccountNumber("1234567890").toString();

        assertThat(texto).doesNotContain("1234567890").contains("7890");
    }

    @Test
    void deberia_rechazar_un_numero_de_cuenta_con_letras() {
        assertThatThrownBy(() -> new BankAccountNumber("12345678AB")).isInstanceOf(IllegalArgumentException.class);
    }

    // --- Nombre legal y RN-012 ----------------------------------------------

    @Test
    void deberia_cumplir_RN_012_ignorando_acentos_y_mayusculas() {
        assertThat(new LegalName("José Pérez").coincideCon(new LegalName("JOSE PEREZ")))
                .isTrue();
    }

    @Test
    void deberia_cumplir_RN_012_ignorando_espacios_de_sobra_y_puntuacion() {
        assertThat(new LegalName("  Maria  del Carmen  Gomez ").coincideCon(new LegalName("Maria del Carmen Gomez.")))
                .isTrue();
    }

    /** El guion de un apellido compuesto separa palabras igual que un espacio. */
    @Test
    void deberia_cumplir_RN_012_tratando_el_guion_como_separador() {
        assertThat(new LegalName("Ana Garcia-Lopez").coincideCon(new LegalName("Ana Garcia Lopez")))
                .isTrue();
    }

    /**
     * La comparacion no es difusa. Un apellido que falta no coincide: nadie ha
     * decidido cuanta diferencia es tolerable, y decidirlo aqui seria inventar la
     * regla.
     */
    @Test
    void deberia_cumplir_RN_012_no_aceptando_un_nombre_incompleto() {
        assertThat(new LegalName("Ana Maria Garcia Lopez").coincideCon(new LegalName("Ana Garcia")))
                .isFalse();
        assertThat(new LegalName("Ana Garcia").coincideCon(new LegalName("A Garcia")))
                .isFalse();
    }

    @Test
    void deberia_rechazar_un_nombre_vacio_o_de_relleno() {
        assertThatThrownBy(() -> new LegalName("   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LegalName("A")).isInstanceOf(IllegalArgumentException.class);
    }

    // --- Codigo de entidad ---------------------------------------------------

    @Test
    void deberia_aceptar_un_codigo_de_entidad_con_forma_de_identificador() {
        assertThatCode(() -> new BankCode("scotiabank-colpatria")).doesNotThrowAnyException();
    }

    @Test
    void deberia_rechazar_un_codigo_de_entidad_que_parece_un_nombre() {
        assertThatThrownBy(() -> new BankCode("Banco de Bogota")).isInstanceOf(IllegalArgumentException.class);
    }

    // --- Documento ------------------------------------------------------------

    /**
     * Es la misma foto subida dos veces: pasa todas las validaciones de imagen y llega
     * a la revision como un documento completo, con el moderador mirando dos veces el
     * mismo lado.
     */
    @Test
    void deberia_rechazar_un_documento_con_la_misma_imagen_en_las_dos_caras() {
        FileKey unica = new FileKey("documentos/abc.jpg");

        assertThatThrownBy(() -> new IdentityDocument(
                        IdentityDocumentType.CC,
                        new IdentityDocumentNumber("1234567"),
                        new LegalName("Ana Garcia"),
                        unica,
                        unica))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("misma imagen");
    }

    @Test
    void deberia_exigir_las_dos_caras_del_documento() {
        assertThatThrownBy(() -> new IdentityDocument(
                        IdentityDocumentType.PPT,
                        new IdentityDocumentNumber("1234567"),
                        new LegalName("Ana Garcia"),
                        new FileKey("documentos/frente.jpg"),
                        null))
                .isInstanceOf(NullPointerException.class);
    }
}
