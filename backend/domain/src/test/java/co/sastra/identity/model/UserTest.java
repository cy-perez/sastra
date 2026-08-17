package co.sastra.identity.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.sastra.identity.exception.UnderageException;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class UserTest {

    private static final LocalDate HOY = LocalDate.of(2026, 8, 17);
    private static final Instant AHORA = Instant.parse("2026-08-17T15:00:00Z");

    private static User registrarConNacimiento(LocalDate nacimiento) {
        return User.registrar(
                UserId.nuevo(),
                new Email("ana@correo.co"),
                new DisplayName("Ana Maria"),
                new BirthDate(nacimiento),
                UserLocale.ES,
                HOY,
                AHORA);
    }

    @Test
    void deberia_crear_la_cuenta_sin_verificar_y_activa() {
        User usuario = registrarConNacimiento(LocalDate.of(1990, 3, 4));

        assertThat(usuario.tieneElCorreoVerificado()).isFalse();
        assertThat(usuario.emailVerifiedAt()).isNull();
        assertThat(usuario.status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void deberia_dar_el_rol_de_comprador_a_toda_cuenta_nueva() {
        assertThat(registrarConNacimiento(LocalDate.of(1990, 3, 4)).roles()).containsExactly(Role.BUYER);
    }

    @Test
    void deberia_rechazar_el_registro_de_un_menor_de_edad_RN_008() {
        assertThatThrownBy(() -> registrarConNacimiento(HOY.minusYears(17))).isInstanceOf(UnderageException.class);
    }

    @Test
    void deberia_aceptar_a_quien_cumple_dieciocho_el_mismo_dia_del_registro_RN_008() {
        assertThat(registrarConNacimiento(HOY.minusYears(18))).isNotNull();
    }

    @Test
    void deberia_rechazar_una_fecha_de_nacimiento_futura_RN_008() {
        assertThatThrownBy(() -> registrarConNacimiento(HOY.plusYears(1))).isInstanceOf(UnderageException.class);
    }

    @Test
    void deberia_marcar_el_correo_como_verificado() {
        User verificado = registrarConNacimiento(LocalDate.of(1990, 3, 4)).conCorreoVerificado(AHORA);

        assertThat(verificado.tieneElCorreoVerificado()).isTrue();
        assertThat(verificado.emailVerifiedAt()).isEqualTo(AHORA);
    }

    // El enlace es de un solo uso, pero si algo llegara dos veces la fecha no
    // debe moverse: la primera verificacion es la buena.
    @Test
    void deberia_ser_idempotente_al_verificar_dos_veces_RN_003() {
        User verificado = registrarConNacimiento(LocalDate.of(1990, 3, 4)).conCorreoVerificado(AHORA);
        User otraVez = verificado.conCorreoVerificado(AHORA.plusSeconds(60));

        assertThat(otraVez.emailVerifiedAt()).isEqualTo(AHORA);
        assertThat(otraVez).isSameAs(verificado);
    }

    @Test
    void deberia_devolver_una_instancia_nueva_al_verificar_y_no_mutar_la_anterior() {
        User sinVerificar = registrarConNacimiento(LocalDate.of(1990, 3, 4));

        assertThat(sinVerificar.conCorreoVerificado(AHORA)).isNotSameAs(sinVerificar);
        assertThat(sinVerificar.tieneElCorreoVerificado()).isFalse();
    }

    @Test
    void deberia_impedir_modificar_los_roles_desde_fuera() {
        User usuario = registrarConNacimiento(LocalDate.of(1990, 3, 4));

        assertThatThrownBy(() -> usuario.roles().add(Role.ADMIN)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void deberia_comparar_por_identificador_y_no_por_contenido() {
        UserId id = UserId.nuevo();
        User uno = User.rehidratar(
                id,
                new Email("ana@correo.co"),
                new DisplayName("Ana"),
                new BirthDate(LocalDate.of(1990, 3, 4)),
                UserLocale.ES,
                UserStatus.ACTIVE,
                null,
                java.util.Set.of(Role.BUYER),
                AHORA);
        User otro = User.rehidratar(
                id,
                new Email("otra@correo.co"),
                new DisplayName("Otra"),
                new BirthDate(LocalDate.of(1991, 3, 4)),
                UserLocale.EN,
                UserStatus.BLOCKED,
                AHORA,
                java.util.Set.of(Role.ADMIN),
                AHORA);

        assertThat(uno).isEqualTo(otro).hasSameHashCodeAs(otro);
    }

    @Test
    void deberia_rehidratar_sin_revalidar_la_edad() {
        // Un menor no puede registrarse, pero si la fecha ya esta en la base hay
        // que poder leerla: revalidar al cargar dejaria la fila inaccesible.
        User menor = User.rehidratar(
                UserId.nuevo(),
                new Email("ana@correo.co"),
                new DisplayName("Ana"),
                new BirthDate(HOY.minusYears(10)),
                UserLocale.ES,
                UserStatus.ACTIVE,
                null,
                java.util.Set.of(Role.BUYER),
                AHORA);

        assertThat(menor.birthDate().esMayorDeEdad(HOY)).isFalse();
    }

    // Este texto acaba en registros de servidor: no puede llevar datos personales.
    @Test
    void no_deberia_exponer_el_correo_ni_el_nombre_al_imprimirse() {
        User usuario = registrarConNacimiento(LocalDate.of(1990, 3, 4));

        assertThat(usuario.toString()).doesNotContain("ana@correo.co").doesNotContain("Ana Maria");
    }
}
