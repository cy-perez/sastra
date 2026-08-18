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
        assertThat(new RefreshTokenId(uuid)).hasToString(uuid.toString());
        assertThat(new TokenFamilyId(uuid)).hasToString(uuid.toString());
        assertThatThrownBy(() -> new ConsentId(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new VerificationTokenId(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RefreshTokenId(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TokenFamilyId(null)).isInstanceOf(NullPointerException.class);
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

    @Test
    void la_ciudad_deberia_quitar_los_espacios_de_los_bordes() {
        assertThat(new City("  Medellin  ").value()).isEqualTo("Medellin");
    }

    // Vacia no es "sin ciudad": para no tenerla, el campo se deja nulo. Aceptar la
    // cadena vacia crearia dos formas de decir lo mismo.
    @Test
    void la_ciudad_deberia_rechazar_un_valor_vacio_o_ausente() {
        assertThatThrownBy(() -> new City("   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new City(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void la_ciudad_deberia_rechazar_un_texto_desmedido() {
        assertThatThrownBy(() -> new City("a".repeat(81))).isInstanceOf(IllegalArgumentException.class);
        assertThat(new City("a".repeat(80)).value()).hasSize(80);
    }

    @Test
    void la_ciudad_deberia_imprimirse_como_su_texto() {
        assertThat(new City("Cali").toString()).isEqualTo("Cali");
    }

    /**
     * Normalizar al entrar evita que el mismo numero exista escrito de cinco
     * formas y que dos personas parezcan tener telefonos distintos.
     */
    @Test
    void el_telefono_deberia_guardarse_solo_con_digitos() {
        assertThat(new Phone("+57 (300) 123-4567").value()).isEqualTo("+573001234567");
        assertThat(new Phone(" 300 123 4567 ").value()).isEqualTo("3001234567");
    }

    @Test
    void el_telefono_deberia_rechazar_lo_que_no_es_un_numero() {
        assertThatThrownBy(() -> new Phone("no-es-numero")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Phone("300 123 456 ext 7")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Phone(null)).isInstanceOf(NullPointerException.class);
    }

    // Ni tan corto que sea un error de tecleo ni tan largo que no exista.
    @Test
    void el_telefono_deberia_exigir_entre_siete_y_quince_digitos() {
        assertThatThrownBy(() -> new Phone("123456")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Phone("1234567890123456")).isInstanceOf(IllegalArgumentException.class);
        assertThat(new Phone("1234567").value()).isEqualTo("1234567");
    }

    /**
     * No se valida contra el plan de numeracion colombiano: un vendedor puede
     * tener un numero de otro pais y este dato no enruta llamadas, solo permite
     * que alguien le escriba.
     */
    @Test
    void el_telefono_deberia_aceptar_un_numero_de_otro_pais() {
        assertThat(new Phone("+34 612 345 678").value()).isEqualTo("+34612345678");
    }

    @Test
    void el_telefono_deberia_imprimirse_como_su_numero() {
        assertThat(new Phone("3001234567").toString()).isEqualTo("3001234567");
    }
}
