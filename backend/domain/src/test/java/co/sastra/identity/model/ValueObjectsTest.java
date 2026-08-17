package co.sastra.identity.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Los objetos de valor pequenos, agrupados: cada uno tiene una sola regla. */
class ValueObjectsTest {

    @Test
    void el_identificador_deberia_construirse_desde_texto() {
        UUID uuid = UUID.randomUUID();

        assertThat(UserId.de(uuid.toString())).isEqualTo(new UserId(uuid));
    }

    @Test
    void el_identificador_deberia_rechazar_un_texto_que_no_es_uuid() {
        assertThatThrownBy(() -> UserId.de("no-soy-un-uuid")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserId.de(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new UserId(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void los_identificadores_deberian_imprimirse_como_su_uuid() {
        UUID uuid = UUID.randomUUID();

        assertThat(new UserId(uuid)).hasToString(uuid.toString());
        assertThat(ConsentId.nuevo().value()).isNotNull();
        assertThat(VerificationTokenId.nuevo().toString()).isNotBlank();
        assertThatThrownBy(() -> new ConsentId(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new VerificationTokenId(null)).isInstanceOf(NullPointerException.class);
    }

    // Si esto se imprimiera, la contrasena acabaria en un registro de servidor.
    @Test
    void la_contrasena_en_claro_no_deberia_imprimirse() {
        assertThat(new RawPassword("secreta12345").toString()).doesNotContain("secreta12345");
    }

    @Test
    void la_contrasena_en_claro_deberia_tener_un_tope_de_largo() {
        assertThatThrownBy(() -> new RawPassword("a".repeat(201))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RawPassword(null)).isInstanceOf(NullPointerException.class);
        assertThat(new RawPassword("a".repeat(200)).largo()).isEqualTo(200);
    }

    @Test
    void el_hash_tampoco_deberia_imprimirse() {
        assertThat(new PasswordHash("$argon2id$v=19$m=16384").toString()).doesNotContain("argon2id");
    }

    @Test
    void el_hash_deberia_rechazar_un_valor_vacio() {
        assertThatThrownBy(() -> new PasswordHash("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PasswordHash(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void el_idioma_deberia_reducir_una_variante_regional_a_su_principal() {
        assertThat(UserLocale.de("es-CO")).isEqualTo(UserLocale.ES);
        assertThat(UserLocale.de("EN")).isEqualTo(UserLocale.EN);
    }

    @Test
    void el_idioma_deberia_caer_en_espanol_cuando_no_reconoce_la_etiqueta() {
        assertThat(UserLocale.de("pt")).isEqualTo(UserLocale.ES);
        assertThat(UserLocale.de(null)).isEqualTo(UserLocale.ES);
        assertThat(UserLocale.de("  ")).isEqualTo(UserLocale.ES);
    }

    @Test
    void el_idioma_deberia_devolver_su_etiqueta_en_minusculas() {
        assertThat(UserLocale.EN.etiqueta()).isEqualTo("en");
    }
}
