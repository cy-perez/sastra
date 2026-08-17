package co.sastra.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.sastra.identity.exception.PasswordTooShortException;
import co.sastra.identity.model.RawPassword;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    @Test
    void deberia_rechazar_una_contrasena_de_menos_de_diez_caracteres_RN_005() {
        assertThatThrownBy(() -> PasswordPolicy.verificar(new RawPassword("nueve car")))
                .isInstanceOf(PasswordTooShortException.class);
    }

    @Test
    void deberia_aceptar_una_contrasena_de_exactamente_diez_caracteres_RN_005() {
        assertThat(PasswordPolicy.cumpleElLargoMinimo(new RawPassword("1234567890")))
                .isTrue();
    }

    // RN-005 es explicita: no se exigen simbolos ni mayusculas obligatorios.
    @Test
    void deberia_aceptar_una_frase_larga_sin_simbolos_ni_mayusculas_RN_005() {
        assertThat(PasswordPolicy.cumpleElLargoMinimo(new RawPassword("caballo bateria grapa")))
                .isTrue();
    }

    @Test
    void deberia_contar_caracteres_y_no_bytes_RN_005() {
        // Diez caracteres con tilde y ene: en UTF-8 ocupan mas de diez bytes.
        assertThat(PasswordPolicy.cumpleElLargoMinimo(new RawPassword("ñáéíóúüñáé")))
                .isTrue();
    }

    @Test
    void deberia_rechazar_una_contrasena_vacia_RN_005() {
        assertThatThrownBy(() -> PasswordPolicy.verificar(new RawPassword("")))
                .isInstanceOf(PasswordTooShortException.class);
    }

    @Test
    void deberia_rechazar_una_contrasena_nula() {
        assertThatThrownBy(() -> PasswordPolicy.verificar(null)).isInstanceOf(NullPointerException.class);
        assertThat(PasswordPolicy.cumpleElLargoMinimo(null)).isFalse();
    }
}
