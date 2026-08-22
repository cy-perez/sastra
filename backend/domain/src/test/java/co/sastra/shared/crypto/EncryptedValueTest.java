package co.sastra.shared.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Un dato cifrado y la version de clave que lo cifro (ADR-0020). */
class EncryptedValueTest {

    @Test
    void deberia_llevar_el_cifrado_y_su_version() {
        assertThatCode(() -> new EncryptedValue("dGV4dG8=", 1)).doesNotThrowAnyException();
    }

    @Test
    void deberia_rechazar_un_cifrado_vacio() {
        assertThatThrownBy(() -> new EncryptedValue("   ", 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EncryptedValue(null, 1)).isInstanceOf(NullPointerException.class);
    }

    /**
     * La version empieza en 1 y no en 0 para que un {@code int} sin inicializar no pase
     * por una version valida: una fila con version 0 no tendria clave con la que
     * descifrarse y el fallo aparaceria el dia que hubiera que leerla.
     */
    @Test
    void deberia_rechazar_una_version_que_no_existe() {
        assertThatThrownBy(() -> new EncryptedValue("dGV4dG8=", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EncryptedValue("dGV4dG8=", -1)).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * No es que el cifrado sea secreto —para eso esta cifrado— sino que un
     * {@code toString} util aqui invita a interpolar el objeto en un registro, y de ahi
     * a interpolar el valor en claro hay un paso.
     */
    @Test
    void deberia_ocultar_el_cifrado_al_convertirlo_en_texto() {
        String texto = new EncryptedValue("dGV4dG8=", 3).toString();

        assertThat(texto).doesNotContain("dGV4dG8=").contains("v3");
    }

    @Test
    void deberia_comparar_por_valor() {
        assertThat(new EncryptedValue("dGV4dG8=", 1)).isEqualTo(new EncryptedValue("dGV4dG8=", 1));
        assertThat(new EncryptedValue("dGV4dG8=", 1)).isNotEqualTo(new EncryptedValue("dGV4dG8=", 2));
    }
}
